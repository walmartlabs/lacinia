(ns com.walmartlabs.lacinia.executor.standard
  "Mechanisms for executing parsed queries against compiled schemas."
  (:require
    [com.walmartlabs.lacinia.executor.common :as executor.common]
    [com.walmartlabs.lacinia.internal-utils :refer [seqv cond-let to-message map-vals]]
    [flatland.ordered.map :refer [ordered-map]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.lacinia.constants :as constants]))


(declare resolve-and-select)

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
  (let [{:keys [context resolved-value]} selection-tuple
        {:keys [alias]} field-selection
        non-nullable-field? (-> field-selection :field-definition :non-nullable?)
        tuple (resolve-and-select context field-selection resolved-value)
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
      (let [resolver-result (resolve-and-select context fragment-selection resolved-value)]
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

;; —————————————————————————————————————————————————————————————————————————————
;; ## Standard Strategy

;; This StandardStrategy has _some_ execution logic that should be under the
;; purview of the validator; ignore for now.
;; Some of this could be refactored for the async execution model as well.

;; Some refactoring is due here. It would be better to start with a SelectionTuple, it would make
;; the overall process more clean and easier to follow.

(defn ^:private resolve-and-select
  "Recursive resolution of a field within a containing field's resolved value.

  Returns a ResolverResult of the selected value and any errors."
  [context selection value]
  (cond-let
    :let [result (executor.common/resolve-value selection context value)
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
                                          (->SelectionTuple context selected-base resolved-value [])
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
      (resolve/resolve-as selected-values errors))

    ;; Otherwise, it is just a map but must still have selections applied, to form the selected value.
    :else
    (let [[selected-value errors] (selected-value-builder resolved-value)
          errors' (-> resolve-errors
                      (into errors)
                      seqv)]
      (resolve/resolve-as selected-value errors'))))

(defn execute-query
  "Entrypoint for execution of a query.

  Executes a parsed query using the provided executor context
  (via [[com.walmartlabs.lacinia.executor/executor-context]].

  Returns a query result, with :data and/or :errors keys."
  [context]
  (let [schema (get context constants/schema-key)
        selections (get-in context [constants/parsed-query-key :selections])]
    (reduce (fn [root-result query-node]
              (if (:disabled? query-node)
                root-result
                (let [result (resolve-and-select context query-node nil)
                      resolve-errors (resolve/resolve-errors result)
                      selected-data (->> result
                                         resolve/resolved-value
                                         (propogate-nulls false)
                                         null-to-nil)]
                  (cond-> root-result
                    true (assoc-in [:data (:alias query-node)] selected-data)
                    (seq resolve-errors) (assoc :errors resolve-errors)))))
            {:data nil}
            selections)))
