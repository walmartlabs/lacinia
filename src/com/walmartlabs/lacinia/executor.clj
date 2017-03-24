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

  Returns a schema/ResolvedTuple of the resolved value for the node.
  The error maps in the tuple are enhanced with additional location and query-path data.

  For other types of selections, returns a ResolvedTuple of the value."
  [selection-tuple selection]
  (let [container-value (:resolved-value selection-tuple)]
    (if (= :field (:selection-type selection))
      (let [{:keys [arguments field-definition]} selection
            {:keys [context]} selection-tuple
            schema (get context constants/schema-key)
            resolve-context (assoc context :com.walmartlabs.lacinia/selection selection)]
        (try
          (let [resolve (field-selection-resolver schema selection container-value)
                tuple (resolve resolve-context arguments container-value)]
            (resolve-as (resolve/resolved-value tuple)
                        (enhance-errors selection (resolve/resolve-errors tuple))))
          (catch clojure.lang.ExceptionInfo e
            ;; TODO: throw-ing will be a problem once we get into async
            (throw (ex-info (str "Error resolving field: " (to-message e))
                            (ex-info-map selection (ex-data e)))))))
      ;; Else, not a field selection:
      (resolve-as container-value))))

(declare ^:private resolve-and-select)

(defrecord SelectionTuple
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

(defmulti ^:private apply-selection
  "Applies a selection on a resolved value.

  Returns the updated selection tuple."

  (fn [selection-tuple selection]
    (:selection-type selection)))


(defmethod apply-selection :field
  [selection-tuple field-selection]
  (let [{:keys [alias]} field-selection
        non-nullable-field? (-> field-selection :field-definition :non-nullable?)
        tuple (resolve-and-select selection-tuple field-selection)
        sub-selection (resolve/resolved-value tuple)
        errors (resolve/resolve-errors tuple)
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
    (-> selection-tuple
        (assoc-in [:value alias] sub-selection')
        (update :errors into errors))))

(defn ^:private maybe-apply-fragment
  [selection-tuple fragment-selection concrete-types]
  (let [{:keys [context resolved-value]} selection-tuple
        actual-type (schema/type-tag resolved-value)]
    (if (contains? concrete-types actual-type)
      ;; Note: Ideally, we could pass the selection-tuple into resolve-and-select directly, rather than having
      ;; to merge the results back together. That's the refactoring note a bit below.
      (let [resolver-result (resolve-and-select selection-tuple fragment-selection)]
        (-> selection-tuple
            (update :value merge (resolve/resolved-value resolver-result))
            (update :errors into (resolve/resolve-errors resolver-result))))
      ;; Not an applicable type
      selection-tuple)))

(defmethod apply-selection :inline-fragment
  [selection-tuple inline-fragment-selection]
  (maybe-apply-fragment selection-tuple
                        inline-fragment-selection
                        (:concrete-types inline-fragment-selection)))

(defmethod apply-selection :fragment-spread
  [selection-tuple fragment-spread-selection]
  (let [{:keys [fragment-name]} fragment-spread-selection
        fragment-def (get-in selection-tuple [:context constants/parsed-query-key :fragments fragment-name])]
    (maybe-apply-fragment selection-tuple
                          ;; A bit of a hack:
                          (assoc fragment-spread-selection
                                 :selections (:selections fragment-def))
                          (:concrete-types fragment-def))))


(defn ^:private maybe-apply-selection
  [selection-tuple selection]
  ;; :disabled? may be set by a directive
  (if (:disabled? selection)
    selection-tuple
    (apply-selection selection-tuple selection)))

;; Some refactoring is due here. It would be better to start with a SelectionTuple, it would make
;; the overall process more clean and easier to follow.

(defn ^:private resolve-and-select
  "Recursive resolution of a field within a containing field's resolved value.

  Returns a ResolverResult of the selected value and any errors."
  [selection-tuple selection]
  (cond-let
    :let [result (resolve-value selection-tuple selection)
          resolved-value (resolve/resolved-value result)
          sub-selections (:selections selection)]

    ;; When the value is nil, or there are no sub-selections of the object to
    ;; evaluate, then it's an early, easy finish.
    (or (nil? resolved-value)
        (empty? sub-selections))
    result

    :let [resolve-errors (resolve/resolve-errors result)
          ;; This function takes the resolved value (or, a value from the sequence,
          ;; for a multiple field) and builds out the sub-structure for it, a recursive
          ;; process at the heart of GraphQL.
          ;; It returns a tuple of the selected value
          ;; and any errors for the selected value (or anywhere below).
          selected-value-builder
          (fn [resolved-value]

            (let [selected-base (with-meta (ordered-map) (meta resolved-value))

                  selection-tuple (reduce maybe-apply-selection
                                          (assoc selection-tuple
                                                 :value selected-base
                                                 :resolved-value resolved-value
                                                 :errors [])
                                          sub-selections)

                  {:keys [value errors]} selection-tuple]
              [value errors]))]

    ;; If a field, and the field's type is multiple, then it is a sequence of resolved values
    ;; that must each be selected to form proper results.
    (-> selection :field-definition :multiple?)
    (let [tuples (map selected-value-builder resolved-value)
          selected-values (mapv first tuples)
          errors (->> (mapv second tuples)
                      (reduce into resolve-errors)
                      seqv)]
      (resolve-as selected-values errors))

    ;; Otherwise, it is just a map but must still have selections applied, to form the selected value.
    :else
    (let [[selected-value errors] (selected-value-builder resolved-value)
          errors' (-> resolve-errors
                      (into errors)
                      seqv)]
      (resolve-as selected-value errors'))))

(defn execute-query
  "Entrypoint for execution of a query.

  Expects the context to contain the schema and parsed query.

  Returns a query result, with :data and/or :errors keys."
  [context]
  (let [schema (get context constants/schema-key)
        selections (get-in context [constants/parsed-query-key :selections])]
    (reduce (fn [root-result query-node]
              (if (:disabled? query-node)
                root-result
                (let [root-selection-tuple (->SelectionTuple context (ordered-map) nil [])
                      result (resolve-and-select root-selection-tuple query-node)
                      resolve-errors (resolve/resolve-errors result)
                      selected-data (->> result
                                         resolve/resolved-value
                                         (propogate-nulls false)
                                         null-to-nil)]
                  (cond-> root-result
                    true (assoc-in [:data (:alias query-node)] selected-data)
                    (seq resolve-errors) (update :errors into resolve-errors)))))
            {:data nil}
            selections)))
