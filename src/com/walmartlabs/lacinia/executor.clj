(ns com.walmartlabs.lacinia.executor
  "Mechanisms for executing parsed queries against compiled schemas."
  (:require
    [com.walmartlabs.lacinia.internal-utils
     :refer [cond-let map-vals remove-vals q]]
    [flatland.ordered.map :refer [ordered-map]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.resolve :as resolve
     :refer [resolve-as combine-results]]
    [com.walmartlabs.lacinia.constants :as constants])
  (:import
    (clojure.lang PersistentQueue)
    (com.walmartlabs.lacinia.resolve ResolveCommand)))

(defn ^:private ex-info-map
  [field-selection]
  (remove-vals nil? {:locations [(:location field-selection)]
                     :query-path (:query-path field-selection)
                     :arguments (:reportable-arguments field-selection)}))

(defn ^:private assert-and-wrap-error
  "An error returned by a resolver should be nil, a map, or a collection
  of maps. These maps should contain a :message key, but may contain others.
  Wrap them in a vector if necessary.

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
                         "each containing, at minimum, a :message key.")
                    {:error error-map-or-maps}))))

(defn ^:private enhance-errors
  "From an error map, or a collection of error maps, add additional data to
  each error, including location and arguments.  Returns a seq of error maps."
  [field-selection error]
  (let [errors-seq (assert-and-wrap-error error)]
    (when errors-seq
      (let [enhanced-data (ex-info-map field-selection)]
        (map #(merge % enhanced-data) errors-seq)))))

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

    :let [{:keys [field]} field-selection]

    (nil? resolved-type)
    (throw (ex-info "Sanity check: value type tag is nil on abstract type."
                    {:value value}))

    :let [type (get schema resolved-type)]

    (nil? type)
    (throw (ex-info "Sanity check: invalid type tag on value."
                    {:type-name resolved-type
                     :value value}))

    :else
    (or (get-in type [:fields field :resolve])
        (throw (ex-info "Sanity check: field not present."
                        {:type resolved-type
                         :value value})))))

(defn ^:private invoke-resolver-for-field
  "Resolves the value for a field selection node, by passing the value to the
  appropriate resolver, and passing it through a chain of value enforcers.

  Returns a ResolverResult.
  The final ResolverResult will always contain just a resolved value; errors are handled here,
  and not passed back in the returned ResolverResult.

  Optionally updates the timings inside the execution-context with start/finish/elapsed time
  (in milliseconds). Timing checks only occur when enabled (timings is non-nil)
  and not for default resolvers.

  Any resolve errors are enhanced with details about the selection and accumulated in
  the execution context."
  [execution-context field-selection]
  (let [timings (:timings execution-context)
        {:keys [arguments]} field-selection
        container-value (:resolved-value execution-context)
        {:keys [context]} execution-context
        schema (get context constants/schema-key)
        resolved-type (:resolved-type execution-context)
        resolve-context (assoc context
                               :com.walmartlabs.lacinia/container-type-name resolved-type
                               constants/selection-key field-selection)
        field-resolver (field-selection-resolver schema field-selection resolved-type container-value)
        start-ms (when (and (some? timings)
                            (not (-> field-resolver meta ::schema/default-resolver?)))
                   (System/currentTimeMillis))
        resolver-result (field-resolver resolve-context arguments container-value)
        final-result (resolve/resolve-promise)]
    (resolve/on-deliver! resolver-result
                         (fn [resolved-value]
                           (when start-ms
                             (let [finish-ms (System/currentTimeMillis)
                                   elapsed-ms (- finish-ms start-ms)
                                   timing {:start start-ms
                                           :finish finish-ms
                                           ;; This is just a convenience:
                                           :elapsed elapsed-ms}]
                               ;; The extra key is to handle a case where we time, say, [:hero] and [:hero :friends]
                               ;; That will leave :friends as one child of :hero, and :execution/timings as another.
                               ;; The timings are always a list; we don't know if the field is resolved once,
                               ;; resolved multiple times because it is inside a nested value, or resolved multiple
                               ;; times because of multiple top-level operations.
                               (swap! timings
                                      update-in (conj (:query-path field-selection) :execution/timings)
                                      (fnil conj []) timing)))
                           (resolve/deliver! final-result resolved-value)))
    final-result))

(declare ^:private resolve-and-select)

(defrecord ExecutionContext
  ;; context, resolved-value, and resolved-type change constantly during the process
  ;; errors is an Atom containing a vector, which accumulates
  ;; error-maps during execution.
  ;; timings is usually nil, or may be an Atom containing an empty map, which
  ;; accumulates timing data during execution.
  [context resolved-value resolved-type errors timings])

(defn ^:private null-to-nil
  [v]
  (cond
    (vector? v)
    (map null-to-nil v)

    (= ::null v)
    nil

    :else
    v))

(defn ^:private propogate-nulls
  "When all values for a selected value are ::null, it is replaced with
  ::null (if non-nullable) or nil (if nullable).

  Otherwise, the selected values are a mix of real values and ::null, so replace
  the ::null values with nil."
  [non-nullable? selected-value]
  (cond
    ;; This sometimes happens when a field returns multiple scalars:
    (not (map? selected-value))
    selected-value

    (and (seq selected-value)
         (every? (fn [[_ v]] (= v ::null))
                 selected-value))
    (if non-nullable? ::null nil)

    :else
    (map-vals null-to-nil selected-value)))

(defmulti ^:private apply-selection
  "Applies a selection on a resolved value.

   The execution context contains the resolved value as key :resolved-value.

   Runs the selection, returning a ResolverResult of a map of key/values to add
   to the container value.
   For a field, the map will be a single key and value.
   For a fragment, the map will contain multiple keys and values.

   May return nil for a disabled selection."
  (fn [execution-context selection]
    (:selection-type selection)))

(defmethod apply-selection :field
  [execution-context field-selection]
  (let [{:keys [alias]} field-selection
        non-nullable-field? (-> field-selection :field-definition :type :kind (= :non-null))
        resolver-result (resolve-and-select execution-context field-selection)
        final-result (resolve/resolve-promise)]
    (resolve/on-deliver! resolver-result
                         (fn [resolved-field-value]
                           (let [sub-selection (cond
                                                 (and non-nullable-field?
                                                      (nil? resolved-field-value))
                                                 ::null

                                                 ;; child field was non-nullable and resolved to null,
                                                 ;; but parent is nullable so let's null parent
                                                 (and (= resolved-field-value ::null)
                                                      (not non-nullable-field?))
                                                 nil

                                                 (map? resolved-field-value)
                                                 (propogate-nulls non-nullable-field? resolved-field-value)

                                                 ;; TODO: We also support sets
                                                 (vector? resolved-field-value)
                                                 (mapv #(propogate-nulls non-nullable-field? %) resolved-field-value)

                                                 :else
                                                 resolved-field-value)]
                             (resolve/deliver! final-result (hash-map alias sub-selection)))))
    final-result))

(defn ^:private maybe-apply-fragment
  [execution-context fragment-selection concrete-types]
  (let [actual-type (:resolved-type execution-context)]
    (when (contains? concrete-types actual-type)
      (resolve-and-select execution-context fragment-selection))))

(defmethod apply-selection :inline-fragment
  [execution-context inline-fragment-selection]
  (maybe-apply-fragment execution-context
                        inline-fragment-selection
                        (:concrete-types inline-fragment-selection)))

(defmethod apply-selection :fragment-spread
  [execution-context fragment-spread-selection]
  (let [{:keys [fragment-name]} fragment-spread-selection
        fragment-def (get-in execution-context [:context constants/parsed-query-key :fragments fragment-name])]
    (maybe-apply-fragment execution-context
                          ;; A bit of a hack:
                          (assoc fragment-spread-selection
                                 :selections (:selections fragment-def))
                          (:concrete-types fragment-def))))


(defn ^:private maybe-apply-selection
  [execution-context selection]
  ;; :disabled? may be set by a directive
  (when-not (:disabled? selection)
    (apply-selection execution-context selection)))

(defn ^:private combine-map-results
  "Left associative resolution of results, combined using merge."
  [left-result right-result]
  (combine-results merge left-result right-result))

(defn ^:private execute-nested-selections
  "Executes nested sub-selections once a value is resolved.

  Returns a ResolverResult whose value is a map of keys and selected values."
  [execution-context sub-selections]
  ;; First step is easy: convert the selections into ResolverResults.
  ;; Then a cascade of intermediate results that combine the individual results
  ;; in the correct order.
  (let [selection-results (keep #(maybe-apply-selection execution-context %) sub-selections)]
    (reduce combine-map-results
            (resolve-as (ordered-map))
            selection-results)))

(defn ^:private combine-selection-results-sync
  [execution-context previous-resolved-result sub-selection]
  ;; Let's just call the previous result "left" and the sub-selection's result "right".
  ;; However, sometimes a selection is disabled and returns nil instead of a ResolverResult.
  (let [next-result (resolve/resolve-promise)]
    (resolve/on-deliver! previous-resolved-result
                         (fn [left-map]
                           ;; This is what makes it sync: we don't kick off the evaluation of the selection
                           ;; until the previous selection, left, has completed.
                           (let [sub-resolved-result (apply-selection execution-context sub-selection)]
                             (resolve/on-deliver! sub-resolved-result
                                                  (fn [right-map]
                                                    (resolve/deliver! next-result
                                                                      (merge left-map right-map)))))))
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
  (let
    [is-fragment? (-> selection :selection-type (not= :field))
     sub-selections (:selections selection)
     ;; The callback is a wrapper around the builder, that handles the optional
     ;; errors.
     selector-callback
     (fn selector-callback [{:keys [errors resolved-value resolved-type execution-context]}]
       ;; Any errors from the resolver (via with-errors) or anywhere along the
       ;; selection pipeline are enhanced and added to the execution context.
       (when errors
         (->> errors
              (mapcat #(enhance-errors selection %))
              (swap! (:errors execution-context) into)))

       (cond

         (= ::schema/empty-list resolved-value)
         (resolve-as [])

         (and (some? resolved-value)
              (seq sub-selections))
         (execute-nested-selections
           (assoc execution-context
                  :resolved-value resolved-value
                  :resolved-type resolved-type)
           sub-selections)

         :else
         (resolve/resolve-as resolved-value)))
     ;; In a concrete type, we know the selector from the field definition
     ;; (a field definition on a concrete object type).  Otherwise, we need
     ;; to use the type of the parent node's resolved value, just
     ;; as we do to get a resolver.
     resolved-type (:resolved-type execution-context)
     selector (if is-fragment?
                schema/floor-selector
                (or (-> selection :field-definition :selector)
                    (let [field-name (:field selection)]
                      (-> execution-context
                          :context
                          (get constants/schema-key)
                          (get resolved-type)
                          :fields
                          (get field-name)
                          :selector
                          (or (throw (ex-info "Sanity check: no selector."
                                              {:type-name resolved-type
                                               :selection selection})))))))]

    ;; For fragments, we start with a single value and it passes right through to
    ;; sub-selections, without changing value or type.
    (if is-fragment?
      (selector {:resolved-value (:resolved-value execution-context)
                 :resolved-type resolved-type
                 :callback selector-callback
                 :execution-context execution-context})

      ;; Here's where it comes together.  The field's selector
      ;; does the validations, and for list types, does the mapping.
      ;; It also figures out the field type.
      ;; Eventually, individual values will be passed to the callback, which can then turn around
      ;; and recurse down a level.  The result is a map or a list of maps.

      (let [final-result (resolve/resolve-promise)]
        (resolve/on-deliver! (invoke-resolver-for-field execution-context selection)
                             (fn receive-resolved-value-from-field [resolved-value]
                               (loop [resolved-value resolved-value
                                      selector-context {:execution-context execution-context}]
                                 ;; Using satisfies? is a huge performance hit. ResolveCommand is not
                                 ;; intended as a general extension point, so we don't need to worry about
                                 ;; it being extended to existing types.
                                 (if (instance? ResolveCommand resolved-value)
                                   (recur (resolve/nested-value resolved-value)
                                          (resolve/apply-command resolved-value selector-context))
                                   ;; Finally to a real value, not a wrapper.  The commands may have
                                   ;; modified the :errors or :execution-context keys, and the pipeline
                                   ;; will do the rest. Errors will be dealt with in the callback.
                                   (-> selector-context
                                       (assoc :callback selector-callback
                                              :resolved-value resolved-value)
                                       selector
                                       (resolve/on-deliver!
                                         (fn deliver-selection-for-field [resolved-value]
                                           (resolve/deliver! final-result resolved-value))))))))
        final-result))))

(defn execute-query
  "Entrypoint for execution of a query.

  Expects the context to contain the schema and parsed query.

  Returns a ResolverResult whose value is the query result, with :data and/or :errors keys.

  This should generally not be invoked by user code; see [[execute-parsed-query]]."
  [context]
  (let [parsed-query (get context constants/parsed-query-key)
        {:keys [selections operation-type]} parsed-query
        enabled-selections (remove :disabled? selections)
        errors (atom [])
        timings (when (:com.walmartlabs.lacinia/enable-timing? context)
                  (atom {}))
        context' (assoc context constants/schema-key
                        (get parsed-query constants/schema-key))
        ;; Outside of subscriptions, the ::resolved-value is nil.
        ;; For subscriptions, the :resolved-value will be set to a non-nil value before
        ;; executing the query.
        execution-context (map->ExecutionContext {:context context'
                                                  :errors errors
                                                  :timings timings
                                                  :resolved-value (::resolved-value context)})
        operation-result (if (= :mutation operation-type)
                           (execute-nested-selections-sync execution-context enabled-selections)
                           (execute-nested-selections execution-context enabled-selections))
        response-result (resolve/resolve-promise)]
    (resolve/on-deliver! operation-result
                         (fn [selected-data]
                           (let [data (propogate-nulls false selected-data)]
                             (resolve/deliver! response-result
                                               (cond-> {:data data}
                                                 timings (assoc-in [:extensions :timing] @timings)
                                                 (seq @errors) (assoc :errors (distinct @errors)))))))
    response-result))

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

    (:field :inline-fragment) (:selections node)

    :fragment-spread
    (let [{:keys [fragment-name]} node]
      (get-in parsed-query [:fragments fragment-name :selections]))))

(defn ^:private to-field-name
  "Identifies the qualified field name for a selection node.  May return nil
  for meta-fields such as __typename."
  [node]
  (-> node :field-definition :qualified-field-name))

(defn selections-seq
  "A width-first traversal of selections tree, returning a lazy sequence
  of qualified field names.  A qualified field name is a namespaced keyword,
  the namespace is the containing type, e.g. :User/name."
  {:added "0.17.0"}
  [context]
  (let [parsed-query (get context constants/parsed-query-key)
        selection (get context constants/selection-key)
        step (fn step [queue]
               (when (seq queue)
                 (let [node (peek queue)]
                   (cons node
                         (step (into (pop queue)
                                     (node-selections parsed-query node)))))))]
    (->> (conj PersistentQueue/EMPTY selection)
         step
         ;; remove the first node (the selection); just interested
         ;; in what's beneath the selection
         next
         (filter #(= :field (:selection-type %)))
         (keep to-field-name))))

(defn selects-field?
  "Invoked by a field resolver to determine if a particular field is selected anywhere within the selection
   tree (that is, at any depth)."
  {:added "0.17.0"}
  [context field-name]
  (boolean (some #(= field-name %) (selections-seq context))))

(defn ^:private build-selections-map
  "Builds the selections map for a field selection node."
  [parsed-query selections]
  (reduce (fn [m selection]
            (case (:selection-type selection)

              :field
              (if-some [field-name (to-field-name selection)]
                (assoc m field-name
                       (let [arguments (:arguments selection)
                             nested-map (build-selections-map parsed-query (:selections selection))]
                         (cond-> nil
                           (not (empty? arguments)) (assoc :args arguments)
                           (not (empty? nested-map)) (assoc :selections nested-map))))
                m)

              :inline-fragment
              (merge m (build-selections-map parsed-query (:selections selection)))

              :fragment-spread
              (let [{:keys [fragment-name]} selection
                    fragment-selections (get-in parsed-query [:fragments fragment-name :selections])]
                (merge m (build-selections-map parsed-query fragment-selections)))))
          {}
          selections))

(defn selections-tree
  "Constructs a tree of the selections below the current field.

   Returns a map where the keys are qualified field names (the selections for this field).
   For each such field is a map with two optional keys; :args and :selections.
   :args is the arguments that will be passed to that field's resolver.
   :selections is the nested selection tree.

   Each key is present only if value is provided; for scalar fields with no arguments, the
   value will be nil.

   Fragments are flattened into containing fields, as with `selections-seq`."
  {:added "0.17.0"}
  [context]
  (let [parsed-query (get context constants/parsed-query-key)
        selection (get context constants/selection-key)]
    (build-selections-map parsed-query (:selections selection))))

