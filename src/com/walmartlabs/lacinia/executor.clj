(ns com.walmartlabs.lacinia.executor
  "Mechanisms for executing parsed queries against compiled schemas."
  (:require
    [com.walmartlabs.lacinia.internal-utils
     :refer [seqv ensure-seq cond-let to-message map-vals remove-vals q]]
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

  Returns a resolve/ResolvedTuple of the resolved value for the node.

  Any resolve errors are enhanced with details about the selection and accumulated in
  the execution context.

  For other types of selections, returns a ResolvedTuple of the value."
  [execution-context selection]
  (let [container-value (:resolved-value execution-context)]
    (if (= :field (:selection-type selection))
      (let [{:keys [arguments field-definition]} selection
            {:keys [context]} execution-context
            schema (get context constants/schema-key)
            resolve-context (assoc context :com.walmartlabs.lacinia/selection selection)]
        (try
          (let [resolve (field-selection-resolver schema selection container-value)
                tuple (resolve resolve-context arguments container-value)
                errors (-> tuple resolve/resolve-errors seq)]
            (if errors
              (do
                (swap! (:errors execution-context) into
                       (enhance-errors selection errors))
                (resolve-as (resolve/resolved-value tuple)))
              tuple))
          (catch clojure.lang.ExceptionInfo e
            ;; TODO: throw-ing will be a problem once we get into async
            (throw (ex-info (str "Error resolving field: " (to-message e))
                            (ex-info-map selection (ex-data e)))))))
      ;; Else, not a field selection:
      (resolve-as container-value))))

(declare ^:private resolve-and-select)

(defrecord ExecutionContext
  [context value resolved-value errors])

(defn ^:private null-to-nil
  [v]
  (if (= ::null v)
    nil
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
    (map-vals (fn [v]
                (if (vector? v)
                  (mapv null-to-nil v)
                  (null-to-nil v)))
              selected-value)))

(defn ^:private accumulate-errors
  [execution-context resolved-tuple]
  (when-let [errors (-> resolved-tuple
                        resolve/resolve-errors
                        seq)]
    (swap! (:errors execution-context) into errors)))

(defmulti ^:private apply-selection
  "Applies a selection on a resolved value.

  Returns the updated selection context."
  (fn [execution-context selection]
    (:selection-type selection)))

(defmethod apply-selection :field
  [execution-context field-selection]
  (let [{:keys [alias]} field-selection
        non-nullable-field? (-> field-selection :field-definition :non-nullable?)
        tuple (resolve-and-select execution-context field-selection)
        sub-selection (resolve/resolved-value tuple)
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

(defn ^:private resolve-and-select
  "Recursive resolution of a field within a containing field's resolved value.

  Returns a ResolverResult of the selected value.
  Accumulates errors in the execution context as a side-effect."
  [execution-context selection]
  (cond-let
    :let [resolved-tuple (resolve-value execution-context selection)
          resolved-value (resolve/resolved-value resolved-tuple)
          sub-selections (:selections selection)]

    ;; When the value is nil, or there are no sub-selections of the object to
    ;; evaluate, then it's an early, easy finish.
    (or (nil? resolved-value)
        (empty? sub-selections))
    resolved-tuple

    :let [selected-value-builder
          ;; This function takes the resolved value (or, a value from the sequence,
          ;; for a multiple field) and builds out the sub-structure for it, a recursive
          ;; process at the heart of GraphQL.
          ;; It returns the selected value, and adds errors to the execution-errors
          ;; atom as a side-effect.
          (fn [resolved-value]

            (let [selected-base (with-meta (ordered-map) (meta resolved-value))

                  execution-context (reduce maybe-apply-selection
                                            (assoc execution-context
                                                   :value selected-base
                                                   :resolved-value resolved-value)
                                            sub-selections)]
              (:value execution-context)))]

    ;; If a field, and the field's type is multiple, then it is a sequence of resolved values
    ;; that must each be selected to form proper results.
    (-> selection :field-definition :multiple?)
    (let [selected-values (mapv selected-value-builder resolved-value)]
      (resolve-as selected-values))

    ;; Otherwise, it is just a map but must still have selections applied, to form the selected value.
    :else
    (-> resolved-value
        selected-value-builder
        resolve-as)))

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
                                 result (resolve-and-select root-execution-context query-node)
                                 selected-data (->> result
                                                    resolve/resolved-value
                                                    (propogate-nulls false)
                                                    null-to-nil)]
                             (assoc-in root-result [:data (:alias query-node)] selected-data))))
                       {:data nil}
                       selections)]
    (cond-> result
      (seq @errors) (assoc :errors (distinct @errors)))))
