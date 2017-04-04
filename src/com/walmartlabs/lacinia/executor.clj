(ns com.walmartlabs.lacinia.executor
  "Mechanisms for executing parsed queries against compiled schemas."
  (:require
    [com.walmartlabs.lacinia.internal-utils
     :refer [cond-let to-message map-vals remove-vals q]]
    [flatland.ordered.map :refer [ordered-map]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.resolve :as resolve
     :refer [resolve-as]]
    [com.walmartlabs.lacinia.constants :as constants]))

(defn ^:private ex-info-map
  ([field-selection]
   (ex-info-map field-selection {}))
  ([field-selection m]
   (merge m
          {:locations [(:location field-selection)]}
          (->> (select-keys field-selection [:query-path :arguments])
               (remove-vals nil?)))))

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
  "Using a (resolved) collection of (wrapped) error maps, add additional data to
  each error, including location and arguments."
  [field-selection error-maps]
  (when (seq error-maps)
    (let [enhanced-data (ex-info-map field-selection nil)]
      (map
        #(merge % enhanced-data)
        error-maps))))

(defn ^:private field-selection-resolver
  "Returns the field resolver for the provided field selection.

  When the field-selection is on a concrete type, the resolve from the
  nested field-definition is returned.

  When the field selection is on an abstract type (an interface or union),
  then the concrete type is extracted from the value instead, and the corresponding
  field of the concrete type is used as the source for the field resolver."
  [schema field-selection value]
  (cond-let
    (:concrete-type? field-selection)
    (-> field-selection :field-definition :resolve)

    :let [{:keys [field]} field-selection
          type-name (schema/type-tag value)]

    (nil? type-name)
    (throw (ex-info "Sanity check: value type tag is nil on abstract type."
                    {:value value
                     :value-meta (meta value)}))

    :let [type (get schema type-name)]

    (nil? type)
    (throw (ex-info "Sanity check: invalid type tag on value."
                    {:type-name type-name
                     :value value
                     :value-meta (meta value)}))

    :else
    (or (get-in type [:fields field :resolve])
        (throw (ex-info "Sanity check: field not present."
                        {:type type-name
                         :value value
                         :value-meta (meta value)})))))


(defn ^:private resolve-value
  "Resolves the value for a field or fragment selection node, by passing the value to the
  appropriate resolver, and passing it through a chain of value enforcers.

  Returns the resolved value.

  Any resolve errors are enhanced with details about the selection and accumulated in
  the execution context."
  [execution-context selection]
  (let [container-value (:resolved-value execution-context)]
    (if (= :field (:selection-type selection))
      (let [{:keys [arguments field-definition]} selection
            {:keys [context]} execution-context
            schema (get context constants/schema-key)
            resolve-context (assoc context :com.walmartlabs.lacinia/selection selection)]
        (try
          (let [field-resolver (field-selection-resolver schema selection container-value)
                resolver-result (field-resolver resolve-context arguments container-value)]
            (when-let [errors (-> resolver-result
                                  resolve/resolve-errors
                                  assert-and-wrap-error
                                  seq)]
              (swap! (:errors execution-context) into
                     (enhance-errors selection errors)))

            (resolve/resolved-value resolver-result))
          (catch clojure.lang.ExceptionInfo e
            ;; TODO: throw-ing will be a problem once we get into async
            (throw (ex-info (str "Error resolving field: " (to-message e))
                            (ex-info-map selection (ex-data e)))))))
      ;; Else, not a field selection, but a fragment selection, which starts with the
      ;; same resolved value as the containing field or selection.
      container-value)))

(declare ^:private resolve-and-select)

(defrecord ExecutionContext
  [context value resolved-value errors])

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
  "When all values for a selected value are ::null, it is replaced with ::null.

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

  Returns the updated selection context."
  (fn [execution-context selection]
    (:selection-type selection)))

(defmethod apply-selection :field
  [execution-context field-selection]
  (let [{:keys [alias]} field-selection
        non-nullable-field? (-> field-selection :field-definition :type :kind (= :non-null))
        resolver-result (resolve-and-select execution-context field-selection)
        sub-selection (resolve/resolved-value resolver-result)
        ;; TODO: I think some of this logic is might be able to move into the selectors.
        sub-selection' (cond
                         (and non-nullable-field?
                              (nil? sub-selection))
                         ::null

                         ;; child field was non-nullable and resolved to null,
                         ;; but parent is nullable so let's null parent
                         (and (= sub-selection ::null)
                              (not non-nullable-field?))
                         nil

                         (map? sub-selection)
                         (propogate-nulls non-nullable-field? sub-selection)

                         (vector? sub-selection)
                         (mapv #(propogate-nulls non-nullable-field? %) sub-selection)

                         :else
                         sub-selection)]
    (assoc-in execution-context [:value alias] sub-selection')))

(defn ^:private maybe-apply-fragment
  [execution-context fragment-selection concrete-types]
  (let [{:keys [context resolved-value]} execution-context
        actual-type (schema/type-tag resolved-value)]
    (if (contains? concrete-types actual-type)
      (let [resolved-tuple (resolve-and-select execution-context fragment-selection)]
        (update execution-context :value merge (resolve/resolved-value resolved-tuple)))
      ;; Not an applicable type
      execution-context)))

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
  (if (:disabled? selection)
    execution-context
    (apply-selection execution-context selection)))

(defn ^:private included-fragment-selector
  [resolved-value callback]
  (callback resolved-value))

(defn ^:private resolve-and-select
  "Recursive resolution of a field within a containing field's resolved value.

  Returns a ResolverResult of the selected value.

  Accumulates errors in the execution context as a side-effect."
  [execution-context selection]
  (let
    [resolved-value (resolve-value execution-context selection)
     sub-selections (:selections selection)

     selected-value-builder
     ;; This function takes the resolved value (or, a value from the list,
     ;; for a list field) and builds out the sub-structure for it, a recursive
     ;; process at the heart of GraphQL.
     ;; It returns the selected value, ready to be attached to the result tree,
     (fn [resolved-value]
       (cond

         (= ::schema/empty-list resolved-value)
         []

         (and (some? resolved-value)
              (seq sub-selections))
         (let [execution-context (reduce maybe-apply-selection
                                         (assoc execution-context
                                                :value (ordered-map)
                                                :resolved-value resolved-value)
                                         sub-selections)]
           (:value execution-context))

         :else
         resolved-value))

     ;; The callback is a wrapper around the builder, that handles the optional
     ;; errors.
     selector-callback
     (fn
       ([resolved-value]
        (selected-value-builder resolved-value))
       ([resolved-value errors]
        (let [errors' (if (map? errors)
                        [errors]
                        errors)]
          (swap! (:errors execution-context)
                 into
                 (enhance-errors selection errors')))
        (selected-value-builder resolved-value)))
     ;; In a concrete type, we know the selector from the field definition
     ;; (a field definition on a concrete object type).  Otherwise, we need
     ;; to use the type of the parent node's resolved value, just
     ;; as we do to get a resolver.
     selector (if (-> selection :selection-type (not= :field))
                included-fragment-selector
                (or (-> selection :field-definition :selector)
                    (let [concrete-type-name (-> execution-context
                                                 :resolved-value
                                                 schema/type-tag)
                          field-name (:field selection)]
                      (-> execution-context
                          :context
                          (get constants/schema-key)
                          (get concrete-type-name)
                          :fields
                          (get field-name)
                          :selector
                          (or (throw (ex-info "Sanity check: no selector."
                                              {:resolved-value resolved-value
                                               :type-name concrete-type-name
                                               :selection selection})))))))]

    ;; Here's where it comes together.  The field's selector
    ;; does the validations, and for list types, does the mapping.
    ;; Eventually, individual values will be passed to the callback, which can then turn around
    ;; and recurse down a level.  The result is a map or a list of maps.

    (resolve-as (selector resolved-value selector-callback))))

(defn execute-query
  "Entrypoint for execution of a query.

  Expects the context to contain the schema and parsed query.

  Returns a query result, with :data and/or :errors keys."
  [context]
  (let [schema (get context constants/schema-key)
        selections (get-in context [constants/parsed-query-key :selections])
        errors (atom [])
        result (reduce (fn [root-result query-node]
                         (if (:disabled? query-node)
                           root-result
                           (let [root-execution-context (->ExecutionContext context (ordered-map) nil errors)
                                 selected-data (->> (apply-selection root-execution-context query-node)
                                                    :value
                                                    (propogate-nulls false))]
                             (update root-result :data merge selected-data))))
                       {:data nil}
                       selections)]
    (cond-> result
      (seq @errors) (assoc :errors (distinct @errors)))))
