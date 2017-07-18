(ns com.walmartlabs.lacinia.validation.scalar-leafs
  {:no-doc true}
  (:require
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.internal-utils :refer [q]]))

(defn ^:private validate-selection
  "Recursively checks if all specified fields are scalar or enum types.
  Non-scalar fields should contain either nested :fields
  or :fragments.
  Fragments are not validated again, only their presence is checked.
  Returns empty sequence if all fields are valid, otherwise returns
  a sequence of error maps, e.g.
  `[{:message \"Field `friends' (of type `character')must have at least one selection.\"
     :locations [{:line 1 :column 7}]}]`"
  [selection]
  (cond
    ;; Fragment spreads do not ever have sub-selections, and are validated
    ;; elsewhere.
    (= :fragment-spread (:selection-type selection))
    []

    (:leaf? selection)
    []

    (seq (:selections selection))
    (mapcat validate-selection (:selections selection))

    :else
    [{:message (format "Field %s (of type %s) must have at least one selection."
                       (-> selection :field (q))
                       (-> selection :field-definition schema/root-type-name q))
      :locations [(:location selection)]}]))

(defn ^:private validate-fragment
  "Validates fragment once to avoid validating it separately for
   each selection."
  [[_ fragment]]
  (validate-selection fragment))

(defn scalar-leafs
  "A GraphQL query is valid only if all leaf nodes (fields without
  sub selections) are of scalar or enum types."
  [prepared-query]
  (let [selections (:selections prepared-query)
        fragments (:fragments prepared-query)]
    (concat
      (mapcat validate-fragment fragments)
      (mapcat validate-selection selections))))
