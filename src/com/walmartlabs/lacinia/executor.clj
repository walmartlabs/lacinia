;; Copyright (c) 2017-present Walmart, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License")
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns com.walmartlabs.lacinia.executor
  "Mechanisms for executing parsed queries against compiled schemas."
  (:require
    [com.walmartlabs.lacinia.internal-utils
     :refer [cond-let q to-message
             deep-merge keepv get-nested]]
    [flatland.ordered.map :refer [ordered-map]]
    [com.walmartlabs.lacinia.select-utils :as su]
    [com.walmartlabs.lacinia.resolve-utils :refer [transform-result aggregate-results]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.resolve :as resolve
     :refer [resolve-as resolve-promise]]
    [com.walmartlabs.lacinia.tracing :as tracing]
    [com.walmartlabs.lacinia.constants :as constants]
    [com.walmartlabs.lacinia.selection :as selection])
  (:import (clojure.lang PersistentQueue)
           (java.util.concurrent Executor)))

(def ^:private empty-ordered-map (ordered-map))

(defn ^:private field-selection-resolver
  "Returns the field resolver for the provided field selection.

  When the field-selection is on a concrete type, the resolve from the
  nested field-definition is returned.

  When the field selection is on an abstract type (an interface or union),
  then the concrete type is extracted from the value instead, and the corresponding
  field of the concrete type is used as the source for the field resolver."
  [schema field-selection container-type container-value]
  (cond-let
    (:concrete-type? field-selection)
    (-> field-selection :field-definition :resolve)

    :let [field-name (get-nested field-selection [:field-definition :field-name])]

    (nil? container-type)
    (throw (ex-info "Sanity check: value type tag is nil on abstract type."
                    {:value container-value}))

    :let [type (get schema container-type)]

    (nil? type)
    (throw (ex-info "Sanity check: invalid type tag on value."
                    {:type-name container-type
                     :value container-value}))

    :else
    (or (get-nested type [:fields field-name :resolve])
        (throw (ex-info "Sanity check: field not present."
                        {:type container-type
                         :value container-value})))))

(defn ^:private invoke-resolver-for-field
  "Resolves the value for a field selection node.

  Returns a ResolverResult.

  Optionally updates the timings inside the execution-context with start/finish/elapsed time
  (in milliseconds). Timing checks only occur when enabled (timings is non-nil)
  and not for default resolvers."
  [execution-context field-selection path container-type container-value]
  (try
    (let [*resolver-tracing (:*resolver-tracing execution-context)
          arguments (selection/arguments field-selection)
          {:keys [context schema]} execution-context
          resolve-context (assoc context
                                 :com.walmartlabs.lacinia/container-type-name container-type
                                 constants/selection-key field-selection)
          field-resolver (field-selection-resolver schema field-selection container-type container-value)]
      (if-not (some? *resolver-tracing)
        (field-resolver resolve-context arguments container-value)
        (let [start-offset (tracing/offset-from-start (:timing-start execution-context))
              start-nanos (System/nanoTime)
              resolver-result (field-resolver resolve-context arguments container-value)]
          ;; If not collecting tracing results, then the resolver-result is all we need.
          ;; Otherwise, we need to create an extra promise so that we can observe the
          ;; delivery of the value to update our timing information. The downside is
          ;; that collecting timing information affects timing.
          (transform-result resolver-result
                            (fn [resolved-value]
                              (let [duration (tracing/duration start-nanos)
                                    {:keys [field-definition]} field-selection
                                    {:keys [field-name type-string]} field-definition]
                                (swap! *resolver-tracing conj
                                       {:path path
                                        :parentType container-type
                                        :fieldName field-name
                                        :returnType type-string
                                        :startOffset start-offset
                                        :duration duration}))
                              resolved-value)))))
    (catch Throwable t
      (let [field-name (get-nested field-selection [:field-definition :qualified-name])
            {:keys [location]} field-selection
            arguments (selection/arguments field-selection)]
        (throw (ex-info (str "Exception in resolver for "
                             (q field-name)
                             ": "
                             (to-message t))
                        {:field-name field-name
                         :arguments arguments
                         :location location
                         :path path}
                        t))))))

(declare ^:private resolve-and-select)

(defrecord ExecutionContext
  ;; context, resolved-value, and resolved-type change constantly during the process
  ;; *errors is an Atom containing a vector, which accumulates
  ;; error-maps during execution.
  ;; *warnings is an Atom containing a vector of warnings (error maps that
  ;; appear in the result as [:extensions :warnings].
  ;; *resolver-tracing is usually nil, or may be an Atom containing an empty map, which
  ;; accumulates timing data during execution.
  ;; *extensions is an Atom containing a map; if non-empty, it is added to the result map as :extensions
  ;; schema is the compiled schema (obtained from the parsed query)
  [context *errors *warnings *extensions *resolver-tracing timing-start schema])

(defn ^:private apply-field-selection
  [execution-context field-selection path container-type container-value]
  (let [alias (selection/alias-name field-selection)
        {:keys [resolve-xf]} field-selection
        {:keys [selector]} (:field-definition field-selection)]
    (resolve-and-select execution-context field-selection false selector (conj path alias) resolve-xf container-type container-value)))

(defn ^:private maybe-apply-fragment
  [execution-context fragment-selection concrete-types path container-type container-value]
  (when (contains? concrete-types container-type)
    (resolve-and-select execution-context fragment-selection true schema/floor-selector path nil container-type container-value)))

(defn ^:private apply-inline-fragment
  [execution-context inline-fragment-selection path container-type container-value]
  (maybe-apply-fragment execution-context
                        inline-fragment-selection
                        (:concrete-types inline-fragment-selection)
                        path container-type container-value))

(defn ^:private apply-named-fragment
  [execution-context named-fragment-selection path container-type container-value]
  (let [{:keys [fragment-name]} named-fragment-selection
        fragment-def (get-nested execution-context [:context constants/parsed-query-key :fragments fragment-name])]
    (maybe-apply-fragment
      execution-context
      ;; A bit of a hack:
      (assoc named-fragment-selection :selections (:selections fragment-def))
      (:concrete-types fragment-def)
      path container-type container-value)))

(defn ^:private apply-selection
  "Applies a selection to the current container-value.

  Returns a ResolverResult that delivers a selected value (usually, a ResultTuple), or may return nil."
  [execution-context selection path container-type container-value]
  (when-not
    (:disabled? selection)
    (case (selection/selection-kind selection)
      :field (apply-field-selection execution-context selection path container-type container-value)

      :inline-fragment (apply-inline-fragment execution-context selection path container-type container-value)

      :named-fragment (apply-named-fragment execution-context selection path container-type container-value))))

(defn ^:private merge-selected-values
  "Merges the left and right values, with a special case for when the right value
  is an ResultTuple."
  [left-value right-value]
  (if (su/is-result-tuple? right-value)
    (let [{:keys [alias value]} right-value
          left-alias-value (alias left-value)]
      (cond
        (= left-alias-value :com.walmartlabs.lacinia.schema/null)
        left-value

        (map? left-alias-value)
        (update left-value alias deep-merge value)

        :else
        (assoc left-value alias value)))
    (deep-merge left-value right-value)))

(defn ^:private execute-nested-selections
  "Executes nested sub-selections once a value is resolved.

  Returns a ResolverResult delivering an ordered map of keys and selected values."
  [execution-context sub-selections path resolve-xf container-type container-value]
  ;; First step is easy: convert the selections into ResolverResults.
  ;; Then once all the individual results are ready, combine them in the correct order.
  (let [selection-results (keepv #(apply-selection execution-context % path container-type container-value) sub-selections)]
    (aggregate-results selection-results
                       (fn [values]
                         (cond-> (reduce merge-selected-values empty-ordered-map values)
                           resolve-xf resolve-xf)))))

(defn ^:private combine-selection-results-sync
  [execution-context previous-resolved-result sub-selection path container-type container-value]
  ;; Let's just call the previous result "left" and the sub-selection's result "right".
  ;; However, sometimes a selection is disabled and returns nil instead of a ResolverResult.
  (let [next-result (resolve-promise)]
    (resolve/on-deliver! previous-resolved-result
                         (fn [left-value]
                           ;; This is what makes it sync: we don't kick off the evaluation of the selection
                           ;; until the previous selection, left, has completed.
                           (let [sub-resolved-result (apply-selection execution-context sub-selection path container-type container-value)]
                             (resolve/on-deliver! sub-resolved-result
                                                  (fn [right-value]
                                                    (resolve/deliver! next-result
                                                                      (merge-selected-values left-value right-value)))))))
    ;; This will deliver after the sub-selection delivers, which is only after the previous resolved result
    ;; delivers.
    next-result))

(defn ^:private execute-nested-selections-sync
  "Used to execute top-level mutation operations in synchronous order.

  sub-selections is the sequence of top-level operations to execute with disabled operations
  removed.

  Returns ResolverResult whose value is a map of keys and selected values."
  [execution-context sub-selections path _resolve-xf container-type container-value]
  ;; This could be optimized for the very common case of a single sub-selection.
  (reduce #(combine-selection-results-sync execution-context %1 %2 path container-type container-value)
          (resolve-as empty-ordered-map)
          sub-selections))

(defn ^:private resolve-and-select
  "Recursive resolution of a field within a containing field's resolved value.

  Returns a ResolverResult of the selected value.

  Accumulates errors in the execution context as a side-effect."
  [execution-context selection is-fragment? static-selector path resolve-xf container-type container-value]
  (let [;; Get the raw selections (not attached to the schema) which is faster
        sub-selections (:selections selection)

        ;; When an exception occurs at a nested field, we don't want to have the same exception wrapped
        ;; at every containing field, but because (synchronous) selection is highly recursive, that's the danger.
        ;; This is one approach to avoiding that scenario.
        *pass-through-exceptions (atom false)

        ;; The selector pipeline validates the resolved value and handles things like iterating over
        ;; seqs before (repeatedly) invoking the callback, at which point, it is possible to
        ;; perform a recursive selection on the nested fields of the origin field.
        selector-callback
        (fn selector-callback [execution-context path resolve-xf resolved-type resolved-value]
          (reset! *pass-through-exceptions true)
          (cond
            (and (or (some? resolved-value)
                     (= [] path))                           ;; This covers the root operation
                 resolved-type
                 (seq sub-selections))
            ;; Case #1: The field is an object type that needs further sub-selections to reach
            ;; scalar (or enum) leafs.
            (execute-nested-selections execution-context sub-selections path resolve-xf resolved-type resolved-value)
            ;; Case #2: A scalar (or leaf) type, no further sub-selections necessary.

            resolve-xf
            (resolve-as (resolve-xf resolved-value))

            :else
            (resolve-as resolved-value)))
        ;; In a concrete type, we know the selector from the field definition
        ;; (a field definition on a concrete object type).  For a fragment, static-selector
        ;; is schema/floor-selector. Otherwise, we need
        ;; to use the type of the parent node's resolved value, just
        ;; as we do to get a resolver.
        selector (or static-selector
                     (let [field-name (:field-name selection)]
                       (-> execution-context
                           :schema
                           (get container-type)
                           :fields
                           (get field-name)
                           :selector
                           (or (throw (ex-info "Sanity check: no selector."
                                               {:type-name container-type
                                                :selection selection}))))))

        process-resolved-value (fn [resolved-value]
                                 (try
                                   (loop [resolved-value resolved-value
                                          ;; At this point, we only know the new resolved value, it's type
                                          ;; will be established by the selector pipeline.
                                          new-execution-context execution-context]
                                     (if (su/is-wrapped-value? resolved-value)
                                       (recur (:value resolved-value)
                                              (su/apply-wrapped-value new-execution-context selection path resolved-value))
                                       ;; Finally to a real value, not a wrapper:
                                       (selector new-execution-context selection selector-callback path resolve-xf nil resolved-value)))
                                   (catch Throwable t
                                     (if @*pass-through-exceptions
                                       (throw t)
                                       (let [{:keys [location]} selection
                                             arguments (selection/arguments selection)
                                             qualified-name (:qualified-name selection)]
                                         (throw (ex-info (str "Exception processing resolved value for "
                                                              (q qualified-name)
                                                              ": "
                                                              (to-message t))
                                                         {:path path
                                                          :field-name qualified-name
                                                          :arguments arguments
                                                          :location location} t)))))))

        ;; When tracing is enabled, defeat the optimization so that the (trivial) resolver can be
        ;; invoked and its execution time tracked.
        direct-fn (when-not (:*resolver-tracing execution-context)
                    (get-nested selection [:field-definition :direct-fn]))

        ;; Given a ResolverResult from a field resolver, unwrap the field's RR and pass it through process-resolved-value.
        ;; process-resolved-value also returns an RR and chain that RR's delivered value to the RR returned from this function.
        unwrap-resolver-result (fn [field-resolver-result]
                                 (let [final-result (resolve-promise)]
                                   (resolve/on-deliver! field-resolver-result
                                                        (fn receive-resolved-value-from-field [resolved-value]
                                                          (resolve/on-deliver! (process-resolved-value resolved-value)
                                                                               (fn deliver-selection-for-field [resolved-value]
                                                                                 (resolve/deliver! final-result resolved-value)))))
                                   final-result))]

    ;; For fragments, we start with a single value and it passes right through to
    ;; sub-selections, without changing value or type. Ultimately, this will be merged
    ;; into the parent field selection by merge-selected-values.
    (cond

      is-fragment?
      (selector execution-context selection selector-callback path nil container-type container-value)

      ;; Optimization: for simple fields there may be direct function.
      ;; This is a function that synchronously provides the value from the container resolved value.
      ;; This is almost always a default resolver.  The extracted value is passed though to
      ;; the selector, which returns a ResolverResult. Thus we've peeled back at least one layer
      ;; of ResolveResultPromise.
      direct-fn
      (-> container-value direct-fn process-resolved-value)

      ;; Here's where it comes together.  The field's selector
      ;; does the validations, and for list types, does the mapping.
      ;; It also figures out the field type.
      ;; Eventually, individual values will be passed to the callback, which can then turn around
      ;; and recurse down a level.
      ;; The result is a scalar value, a map, or a list of maps or scalar values.

      :else
      (unwrap-resolver-result (invoke-resolver-for-field execution-context selection path container-type container-value)))))

(defn ^:private unwrap-root-value
  "For compatibility reasons, the value passed to a subscriber stream function may be a wrapped value."
  [execution-context selection value]
  (if (su/is-wrapped-value? value)
    (recur (su/apply-wrapped-value execution-context selection [(selection/alias-name selection)] value)
           selection (:value value))
    [execution-context value]))

(defn execute-query
  "Entrypoint for execution of a query.

  Expects the context to contain the schema and parsed query.

  Returns a ResolverResult that will deliver the result map.

  This should generally not be invoked by user code; see [[execute-parsed-query]]."
  [context]
  (let [parsed-query (get context constants/parsed-query-key)
        {:keys [selections operation-type ::tracing/timing-start]} parsed-query
        schema (get parsed-query constants/schema-key)
        ^Executor executor (::schema/executor schema)]
    (binding [resolve/*callback-executor* executor]
      (let [enabled-selections (remove :disabled? selections)
            *errors (atom [])
            *warnings (atom [])
            *extensions (atom {})
            *resolver-tracing (when (::tracing/enabled? context)
                                (atom []))
            context' (assoc context constants/schema-key schema)
            ;; Outside of subscriptions, the ::root-value is nil.
            ;; For subscriptions, the :root-value will be set to a non-nil value before
            ;; executing the query. It may be a wrapped value.
            root-type (get-nested parsed-query [:root :type-name])
            root-value (::resolved-value context)
            execution-context (map->ExecutionContext {:context context'
                                                      :schema schema
                                                      :*errors *errors
                                                      :*warnings *warnings
                                                      :*resolver-tracing *resolver-tracing
                                                      :timing-start timing-start
                                                      :*extensions *extensions})
            [execution-context' root-value'] (unwrap-root-value execution-context (first selections) root-value)
            result-promise (resolve-promise)
            f (bound-fn []
                (try
                  (let [execute-fn (if (= :mutation operation-type) execute-nested-selections-sync execute-nested-selections)
                        operation-result (execute-fn execution-context' enabled-selections [] nil root-type root-value')]
                    (resolve/on-deliver! operation-result
                                         (fn [selected-data]
                                           (let [errors (seq @*errors)
                                                 warnings (seq @*warnings)
                                                 extensions @*extensions]
                                             (resolve/deliver! result-promise
                                                               (cond-> {:data (schema/collapse-nulls-in-map selected-data)}
                                                                 (seq extensions) (assoc :extensions extensions)
                                                                 *resolver-tracing
                                                                 (tracing/inject-tracing timing-start
                                                                                         (::tracing/parsing parsed-query)
                                                                                         (::tracing/validation context)
                                                                                         @*resolver-tracing)
                                                                 errors (assoc :errors (distinct errors))
                                                                 warnings (assoc-in [:extensions :warnings] (distinct warnings))))))))
                  (catch Throwable t
                    (resolve/deliver! result-promise t))))]
        ;; Execute in the background
        (.execute executor f)
        ;; And return a promise
        result-promise))))

(defn invoke-streamer
  "Given a parsed and prepared query (inside the context, as with [[execute-query]]),
  this will locate the streamer for a subscription
  and invoke it, passing it the context, the subscription arguments, and the source stream."
  {:added "0.19.0"}
  [context source-stream]
  (let [parsed-query (get context constants/parsed-query-key)
        {:keys [selections operation-type]} parsed-query
        selection (do
                    (assert (= :subscription operation-type))
                    (first selections))
        streamer (get-nested selection [:field-definition :stream])
        context' (assoc context constants/selection-key selection)]
    (streamer context' (:arguments selection) source-stream)))

(defn ^:private node-selections
  [parsed-query node]
  (case (selection/selection-kind node)

    (:field :inline-fragment) (remove :disabled? (:selections node))

    :named-fragment
    (let [{:keys [fragment-name]} node]
      (get-nested parsed-query [:fragments fragment-name :selections]))))

(defn ^:private to-field-name
  "Identifies the qualified field name for a selection node.  May return nil
  for meta-fields such as __typename."
  [node]
  (get-nested node [:field-definition :qualified-name]))

(defn ^:private walk-selections
  [context node-xform]
  (let [parsed-query (get context constants/parsed-query-key)
        selection (get context constants/selection-key)
        *result (volatile! (transient []))]
    (loop [queue (conj PersistentQueue/EMPTY selection)]
      (if-let [node (peek queue)]
        (let [queue' (-> queue
                         pop
                         (into (node-selections parsed-query node)))
              ;; Skip the initial node; only interested in selections beneath that
              node' (when (and (not (identical? node selection))
                               (= :field (selection/selection-kind node))
                               ;; Skip the __typename psuedo-field
                               (some? (to-field-name node))
                               ;; Nodes are disabled by the @Skip and @Include directives, but
                               ;; that only takes place after the query has been prepared.
                               (not (:disabled? node)))
                      (node-xform node))]
          (when (some? node')
            (vswap! *result conj! node'))
          (recur queue'))
        ;; When queue exhausted:
        (-> *result deref persistent!)))))

(defn selection
  "Returns the field selection, an object that implements the
  [[FieldSelection]], [[SelectionSet]], [[Arguments]], and [[Directives]] protocols."
  [context]
  {:added "0.38.0"}
  (get context constants/selection-key))

(defn selections-seq
  "A width-first traversal of the selections tree, returning a lazy sequence
  of qualified field names.  A qualified field name is a namespaced keyword,
  the namespace is the containing type, e.g. :User/name.

  Fragments are flattened (as if always selected)."
  {:added "0.17.0"}
  [context]
  (walk-selections context to-field-name))

(defn ^:private to-field-data
  [node]
  (let [{:keys [alias arguments]
         simple-field-name :field-name} node]
    (cond-> {:name (to-field-name node)}
      (not (= simple-field-name alias)) (assoc :alias alias)
      (seq arguments) (assoc :args arguments))))

(defn selections-seq2
  "An enhancement of [[selections-seq]] that returns a map for each node:

  :name
  : The qualified field name

  :args
  : The arguments of the field (if any)

  :alias
  : The alias for the field, if any"
  {:added "0.34.0"}
  [context]
  (walk-selections context to-field-data))

(defn selects-field?
  "Invoked by a field resolver to determine if a particular field is selected anywhere within the selection
   tree (that is, at any depth)."
  {:added "0.17.0"}
  [context field-name]
  (boolean (some #(= field-name %) (selections-seq context))))

(defn ^:private conjv
  [coll v]
  (if (nil? coll)
    (vector v)
    (conj coll v)))

(defn ^:private build-selections-map
  "Builds the selections map for a field selection node."
  [parsed-query selections]
  (reduce (fn [m selection]
            (if (:disabled? selection)
              m
              (case (selection/selection-kind selection)

                :field
                ;; to-field-name returns nil for pseudo-fields, which are skipped
                (if-some [field-name (to-field-name selection)]
                  (let [{:keys [alias selections]
                         simple-field-name :field-name} selection
                        arguments (:arguments selection)
                        selections-map (build-selections-map parsed-query selections)
                        nested-map (cond-> nil
                                     (not (= simple-field-name alias)) (assoc :alias alias)
                                     (seq arguments) (assoc :args arguments)
                                     (seq selections-map) (assoc :selections selections-map))]
                    (update m field-name conjv nested-map))
                  m)

                :inline-fragment
                (merge-with into m (build-selections-map parsed-query (:selections selection)))

                :named-fragment
                (let [{:keys [fragment-name]} selection
                      fragment-selections (get-nested parsed-query [:fragments fragment-name :selections])]
                  (merge-with into m (build-selections-map parsed-query fragment-selections))))))
          {}
          selections))

(defn selections-tree
  "Constructs a tree of the selections below the current field.

   Returns a map where the keys are qualified field names (the selections for this field).
   The value is a vector of maps with three optional keys; :args, :alias, and :selections.
   :args is the arguments that will be passed to that field's resolver.
   :selections is the nested selection tree.
   :alias is the alias for the field (most fields do not have aliases).

   A vector is returned because the selection for an outer field may, via aliases, reference
   the same inner field multiple times (with different arguments, aliases, and/or sub-selections).

   Each key of a nested map is present only if a value is provided; for scalar fields with no arguments, the
   nested map will be nil.

   Fragments are flattened into containing fields, as with `selections-seq`."
  {:added "0.17.0"}
  [context]
  (let [parsed-query (get context constants/parsed-query-key)
        selection (get context constants/selection-key)]
    (build-selections-map parsed-query (:selections selection))))

(defrecord ^:private RootSelections [field-definition selections]

  selection/SelectionSet

  ;; Effectively, this is a selection set on the root field (Query, Mutation, or Subscription).
  (selection-kind [_] :field)

  (selections [_] selections))

(defn parsed-query->context
  "Converts a parsed query, prior to execution, into a context compatible with preview API:

  * [[selections-tree]]
  * [[selects-field?]]
  * [[selections-seq]]

  Fields annotated with @skip and @include directives may be omitted if the parsed query
  has also been passed through [[prepare-with-query-variables]], which normally happens
  just before query execution.
  If the query has not been prepared, then the directives are ignored.

  This is used to preview the execution of the query prior to execution."
  {:added "0.34.0"}
  [parsed-query]
  (let [{:keys [root selections]} parsed-query]
    {constants/parsed-query-key parsed-query
     constants/selection-key (->RootSelections root selections)}))
