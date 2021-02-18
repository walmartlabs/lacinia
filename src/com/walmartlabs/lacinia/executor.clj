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
     :refer [cond-let map-vals remove-vals q aggregate-results transform-result to-message]]
    [flatland.ordered.map :refer [ordered-map]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.resolve :as resolve
     :refer [resolve-as resolve-promise]]
    [com.walmartlabs.lacinia.selector-context :as sc]
    [com.walmartlabs.lacinia.tracing :as tracing]
    [com.walmartlabs.lacinia.constants :as constants]
    [com.walmartlabs.lacinia.selection :as selection])
  (:import
    (clojure.lang PersistentQueue)))

(defn ^:private ex-info-map
  [field-selection execution-context]
  (remove-vals nil? {:locations [(:location field-selection)]
                     :path (:path execution-context)
                     :arguments (:reportable-arguments field-selection)}))

(defn ^:private assert-and-wrap-error
  "An error returned by a resolver should be nil, a map, or a collection
  of maps, and the map(s) must contain at least a :message key with a string value.

  Returns nil, or a collection of one or more valid error maps."
  [error-map-or-maps]
  (cond
    (nil? error-map-or-maps)
    nil

    (and (sequential? error-map-or-maps)
         (every? (comp string? :message)
                 error-map-or-maps))
    error-map-or-maps

    (string? (:message error-map-or-maps))
    [error-map-or-maps]

    :else
    (throw (ex-info (str "Errors must be nil, a map, or a sequence of maps "
                         "each containing, at minimum, a :message key and a string value.")
                    {:error error-map-or-maps}))))

(defn ^:private structured-error-map
  "Converts an error map and extra data about location, path, etc. into the
  correct format:  top level keys :message, :path, and :location, and anything else
  under a :extensions key."
  [error-map extra-data]
  (let [{:keys [message extensions]} error-map
        {:keys [locations path]} extra-data
        extensions' (merge (dissoc error-map :message :extensions)
                           (dissoc extra-data :locations :path)
                           extensions)]
    (cond-> {:message message
             :locations locations
             :path path}
      (seq extensions') (assoc :extensions extensions'))))

(defn ^:private enhance-errors
  "From an error map, or a collection of error maps, add additional data to
  each error, including location and arguments.  Returns a seq of error maps."
  [field-selection execution-context error-or-errors]
  (let [errors-seq (assert-and-wrap-error error-or-errors)]
    (when errors-seq
      (let [extra-data (ex-info-map field-selection execution-context)]
        (map #(structured-error-map % extra-data) errors-seq)))))

(defn ^:private field-selection-resolver
  "Returns the field resolver for the provided field selection.

  When the field-selection is on a concrete type, the resolve from the
  nested field-definition is returned.

  When the field selection is on an abstract type (an interface or union),
  then the concrete type is extracted from the value instead, and the corresponding
  field of the concrete type is used as the source for the field resolver."
  [schema field-selection resolved-type value]
  (cond-let
    (:concrete-type? field-selection)
    (-> field-selection :field-definition :resolve)

    :let [field-name (-> field-selection selection/field selection/field-name)]

    (nil? resolved-type)
    (throw (ex-info "Sanity check: value type tag is nil on abstract type."
                    {:value value}))

    :let [type (get schema resolved-type)]

    (nil? type)
    (throw (ex-info "Sanity check: invalid type tag on value."
                    {:type-name resolved-type
                     :value value}))

    :else
    (or (get-in type [:fields field-name :resolve])
        (throw (ex-info "Sanity check: field not present."
                        {:type resolved-type
                         :value value})))))

(defn ^:private invoke-resolver-for-field
  "Resolves the value for a field selection node.

  Returns a ResolverResult.

  Optionally updates the timings inside the execution-context with start/finish/elapsed time
  (in milliseconds). Timing checks only occur when enabled (timings is non-nil)
  and not for default resolvers."
  [execution-context field-selection]
  (try
    (let [*resolver-tracing (:*resolver-tracing execution-context)
          arguments (selection/arguments field-selection)
          container-value (:resolved-value execution-context)
          {:keys [context schema]} execution-context
          resolved-type (:resolved-type execution-context)
          resolve-context (assoc context
                                 :com.walmartlabs.lacinia/container-type-name resolved-type
                                 constants/selection-key field-selection)
          field-resolver (field-selection-resolver schema field-selection resolved-type container-value)]
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
                                       {:path (:path execution-context)
                                        :parentType resolved-type
                                        :fieldName field-name
                                        :returnType type-string
                                        :startOffset start-offset
                                        :duration duration}))
                              resolved-value)))))
    (catch Throwable t
      (let [field-name (get-in field-selection [:field-definition :qualified-name])
            {:keys [location]} field-selection
            arguments (selection/arguments field-selection)]
        (throw (ex-info (str "Exception in resolver for "
                             (q field-name)
                             ": "
                             (to-message t))
                        {:field-name field-name
                         :arguments arguments
                         :location location
                         :path (:path execution-context)}
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
  ;; *extensions is an atom containing a map, if non-empty, it is added to the result map as :extensions
  ;; path is used when reporting errors
  ;; schema is the compiled schema (obtained from the parsed query)
  [context resolved-value resolved-type *errors *warnings *extensions *resolver-tracing path schema])

(defrecord ^:private ResultTuple [alias value])

(defn ^:private apply-field-selection
  [execution-context field-selection]
  (let [{:keys [alias]} field-selection
        null-collapser (get-in field-selection [:field-definition :null-collapser])
        resolver-result (resolve-and-select execution-context field-selection)]
    (transform-result resolver-result
                      (fn [resolved-field-value]
                        (->ResultTuple alias (null-collapser resolved-field-value))))))

(defn ^:private maybe-apply-fragment
  [execution-context fragment-selection concrete-types]
  (let [actual-type (:resolved-type execution-context)]
    (when (contains? concrete-types actual-type)
      (resolve-and-select execution-context fragment-selection))))

(defn ^:private apply-inline-fragment
  [execution-context inline-fragment-selection]
  (maybe-apply-fragment execution-context
                        inline-fragment-selection
                        (:concrete-types inline-fragment-selection)))

(defn ^:private apply-fragment-spread
  [execution-context fragment-spread-selection]
  (let [{:keys [fragment-name]} fragment-spread-selection
        fragment-def (get-in execution-context [:context constants/parsed-query-key :fragments fragment-name])]
    (maybe-apply-fragment execution-context
                          ;; A bit of a hack:
                          (assoc fragment-spread-selection
                                 :selections (:selections fragment-def))
                          (:concrete-types fragment-def))))

(defn ^:private apply-selection
  [execution-context selection]
  (when-not (:disabled? selection)
    (case (:selection-type selection)
      :field (apply-field-selection execution-context selection)

      :inline-fragment (apply-inline-fragment execution-context selection)

      :fragment-spread (apply-fragment-spread execution-context selection))))

(defn ^:private merge-selected-values
  "Merges the left and right values, with a special case for when the right value
  is an ResultTuple."
  [left-value right-value]
  (if (instance? ResultTuple right-value)
    (assoc left-value (:alias right-value) (:value right-value))
    (merge left-value right-value)))

(defn ^:private execute-nested-selections
  "Executes nested sub-selections once a value is resolved.

  Returns a ResolverResult whose value is a map of keys and selected values."
  [execution-context sub-selections]
  ;; First step is easy: convert the selections into ResolverResults.
  ;; Then once all the individual results are ready, combine them in the correct order.
  (let [selection-results (keep #(apply-selection execution-context %) sub-selections)]
    (aggregate-results selection-results
                       (fn [values]
                         (reduce merge-selected-values (ordered-map) values)))))

(defn ^:private combine-selection-results-sync
  [execution-context previous-resolved-result sub-selection]
  ;; Let's just call the previous result "left" and the sub-selection's result "right".
  ;; However, sometimes a selection is disabled and returns nil instead of a ResolverResult.
  (let [next-result (resolve-promise)]
    (resolve/on-deliver! previous-resolved-result
                         (fn [left-value]
                           ;; This is what makes it sync: we don't kick off the evaluation of the selection
                           ;; until the previous selection, left, has completed.
                           (let [sub-resolved-result (apply-selection execution-context sub-selection)]
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
  [execution-context sub-selections]
  ;; This could be optimized for the very common case of a single sub-selection.
  (reduce #(combine-selection-results-sync execution-context %1 %2)
          (resolve-as (ordered-map))
          sub-selections))

(defn ^:private resolve-and-select
  "Recursive resolution of a field within a containing field's resolved value.

  Returns a ResolverResult of the selected value.

  Accumulates errors in the execution context as a side-effect."
  [execution-context selection]
  (let [is-fragment? (not= :field (selection/selection-kind selection))
        ;; When starting to execute a field, add the current alias (or field name) to the path.
        execution-context' (if is-fragment?
                             execution-context
                             (update execution-context :path conj (selection/alias-name selection)))
        sub-selections (selection/selections selection)

        apply-errors (fn [selection-context sc-key ec-atom-key]
                       (when-let [errors (get selection-context sc-key)]
                         (->> errors
                              (mapcat #(enhance-errors selection execution-context' %))
                              (swap! (get execution-context' ec-atom-key) into))))

        ;; When an exception occurs at a nested field, we don't want to have the same exception wrapped
        ;; at every containing field, but because (synchronous) selection is highly recursive, that's the danger.
        ;; This is one approach to avoiding that scenario.
        *pass-through-exceptions (atom false)

        ;; The selector pipeline validates the resolved value and handles things like iterating over
        ;; seqs before (repeatedly) invoking the callback, at which point, it is possible to
        ;; perform a recursive selection on the nested fields of the origin field.
        selector-callback
        (fn selector-callback [{:keys [resolved-value resolved-type execution-context] :as selection-context}]
          (reset! *pass-through-exceptions true)
          ;; Any errors from the resolver (via with-errors) or anywhere along the
          ;; selection pipeline are enhanced and added to the execution context.
          (apply-errors selection-context :errors :*errors)
          (apply-errors selection-context :warnings :*warnings)

          (if (and (or (some? resolved-value)
                       (= [] (:path execution-context)))    ;; This covers the root operation
                   resolved-type
                   (seq sub-selections))
            (execute-nested-selections
              (assoc execution-context
                     :resolved-value resolved-value
                     :resolved-type resolved-type)
              sub-selections)
            (resolve-as resolved-value)))
        ;; In a concrete type, we know the selector from the field definition
        ;; (a field definition on a concrete object type).  Otherwise, we need
        ;; to use the type of the parent node's resolved value, just
        ;; as we do to get a resolver.
        resolved-type (:resolved-type execution-context')
        selector (if is-fragment?
                   schema/floor-selector
                   (or (-> selection :field-definition :selector)
                       (let [field-name (:field-name selection)]
                         (-> execution-context'
                             :schema
                             (get resolved-type)
                             :fields
                             (get field-name)
                             :selector
                             (or (throw (ex-info "Sanity check: no selector."
                                                 {:type-name resolved-type
                                                  :selection selection})))))))

        process-resolved-value (fn [resolved-value]
                                 (try
                                   (loop [resolved-value resolved-value
                                          selector-context (sc/new-context  execution-context' selector-callback)]
                                     (if (sc/is-wrapped-value? resolved-value)
                                       (recur (:value resolved-value)
                                              (sc/apply-wrapped-value selector-context resolved-value))
                                       ;; Finally to a real value, not a wrapper.  The commands may have
                                       ;; modified the :errors or :execution-context keys, and the pipeline
                                       ;; will do the rest. Errors will be dealt with in the callback.
                                       (-> selector-context
                                           (assoc :callback selector-callback
                                                  :resolved-value resolved-value)
                                           selector)))
                                   (catch Throwable t
                                     (if @*pass-through-exceptions
                                       (throw t)
                                       (let [{:keys [location]} selection
                                             arguments (selection/arguments selection)
                                             qualified-name  (:qualified-name selection)]
                                         (throw (ex-info (str "Exception processing resolved value for "
                                                              (q qualified-name)
                                                              ": "
                                                              (to-message t))
                                                         {:path (:path execution-context')
                                                          :field-name qualified-name
                                                          :arguments arguments
                                                          :location location} t)))))))

        ;; Given a ResolverResult from a field resolver, unwrap the field's RR and pass it through process-resolved-value.
        ;; process-resolved-value also returns an RR and chain that RR's delivered value to the RR returned from this function.
        unwrap-resolver-result (fn [field-resolver-result]
                                 (let [final-result (resolve-promise)]
                                   (resolve/on-deliver! field-resolver-result
                                                        (fn receive-resolved-value-from-field [resolved-value]
                                                          ;; This is for a specific case, where a parent resolver returns a map whose value
                                                          ;; is also a ResolverResult; in that case, unwrap one layer further before calling
                                                          ;; process-resolved-value.
                                                          (if (resolve/is-resolver-result? resolved-value)
                                                            (resolve/on-deliver! resolved-value receive-resolved-value-from-field)
                                                            (resolve/on-deliver! (process-resolved-value resolved-value)
                                                                                 (fn deliver-selection-for-field [resolved-value]
                                                                                   (resolve/deliver! final-result resolved-value))))))
                                   final-result))]

    ;; For fragments, we start with a single value and it passes right through to
    ;; sub-selections, without changing value or type.
    (cond

      is-fragment?
      (selector (sc/new-context execution-context'
                                selector-callback
                                (:resolved-value execution-context')
                                resolved-type))

      ;; Here's where it comes together.  The field's selector
      ;; does the validations, and for list types, does the mapping.
      ;; It also figures out the field type.
      ;; Eventually, individual values will be passed to the callback, which can then turn around
      ;; and recurse down a level.
      ;; The result is a scalar value, a map, or a list of maps or scalar values.

      :else
      (unwrap-resolver-result (invoke-resolver-for-field execution-context' selection)))))

(defn execute-query
  "Entrypoint for execution of a query.

  Expects the context to contain the schema and parsed query.

  Returns a ResolverResult that will deliver the result map.

  This should generally not be invoked by user code; see [[execute-parsed-query]]."
  [context]
  (let [parsed-query (get context constants/parsed-query-key)
        {:keys [selections operation-type ::tracing/timing-start]} parsed-query
        enabled-selections (remove :disabled? selections)
        *errors (atom [])
        *warnings (atom [])
        *extensions (atom {})
        *resolver-tracing (when (::tracing/enabled? context)
                            (atom []))
        context' (assoc context constants/schema-key
                        (get parsed-query constants/schema-key))
        ;; Outside of subscriptions, the ::resolved-value is nil.
        ;; For subscriptions, the :resolved-value will be set to a non-nil value before
        ;; executing the query.
        execution-context (map->ExecutionContext {:context context'
                                                  :schema (get parsed-query constants/schema-key)
                                                  :*errors *errors
                                                  :*warnings *warnings
                                                  :*resolver-tracing *resolver-tracing
                                                  :timing-start timing-start
                                                  :*extensions *extensions
                                                  :path []
                                                  :resolved-type (get-in parsed-query [:root :type-name])
                                                  :resolved-value (::resolved-value context)})
        result-promise (resolve-promise)
        executor resolve/*callback-executor*
        f (bound-fn []
            (try
              (let [operation-result (if (= :mutation operation-type)
                                       (execute-nested-selections-sync execution-context enabled-selections)
                                       (execute-nested-selections execution-context enabled-selections))]
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

    (if executor
      (.execute executor f)
      (future (f)))

    result-promise))

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
        streamer (get-in selection [:field-definition :stream])]
    (streamer context (:arguments selection) source-stream)))

(defn ^:private node-selections
  [parsed-query node]
  (case (:selection-type node)

    (:field :inline-fragment) (remove :disabled? (:selections node))

    :fragment-spread
    (let [{:keys [fragment-name]} node]
      (get-in parsed-query [:fragments fragment-name :selections]))))

(defn ^:private to-field-name
  "Identifies the qualified field name for a selection node.  May return nil
  for meta-fields such as __typename."
  [node]
  (get-in node [:field-definition :qualified-name]))

(defn ^:private walk-selections
  [context node-xform]
  (let [parsed-query (get context constants/parsed-query-key)
        selection (get context constants/selection-key)
        step (fn step [queue]
               (when (seq queue)
                 (let [node (peek queue)]
                   (cons node
                         (step (into (pop queue)
                                     (node-selections parsed-query node)))))))
        ;; Filter and transform the node
        f (fn [node]
            (when (and (= :field (:selection-type node))
                       ;; Skip the __typename psuedo-field
                       (some? (to-field-name node))
                       ;; Nodes are disabled by the @Skip and @Include directives, but
                       ;; that only takes place after the query has been prepared.
                       (not (:disabled? node)))
              (node-xform node)))]
    (->> (conj PersistentQueue/EMPTY selection)
         step
         ;; remove the first node (the selection); just interested
         ;; in what's beneath the selection
         next
         (keep f))))

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

(defn ^:private intov
  [coll v]
  (if (nil? coll)
    v
    (into coll v)))

(defn ^:private build-selections-map
  "Builds the selections map for a field selection node."
  [parsed-query selections]
  (reduce (fn [m selection]
            (if (:disabled? selection)
              m
              (case (:selection-type selection)

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
                (merge-with intov m (build-selections-map parsed-query (:selections selection)))

                :fragment-spread
                (let [{:keys [fragment-name]} selection
                      fragment-selections (get-in parsed-query [:fragments fragment-name :selections])]
                  (merge-with intov m (build-selections-map parsed-query fragment-selections))))))
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
     constants/selection-key {:selection-type :field
                              :field-definition root
                              :selections selections}}))


