(ns com.walmartlabs.lacinia.validation.scalar-leafs)

(defn ^:private validate-selection
  "Recursively checks if all specified fields are scalar or enum types.
  Non-scalar fields should contain either nested :fields
  or :fragments.
  Fragments are not validated again, only their presence is checked.
  Returns empty sequence if all fields are valid, otherwise returns
  a sequence of error maps, e.g.
  `[{:message \"Field \"friends\" of type \"character\" must have a sub selection.\"
     :locations [{:line 1 :column 7}]}]`"
  [compiled-schema selection]
  ;; The distinction between fields and fragments is about to go away ...
  (let [subselections (seq (:selections selection))]
    (cond

      ;; Fragment spreads do not ever have sub-selections, and are validated
      ;; elsewhere.
      (= :fragment-spread (:selection-type selection))
      []

      (:leaf? selection)
      []

      subselections
      (mapcat (partial validate-selection compiled-schema) subselections)

      :else
      [{:message (format "Field \"%s\" of type \"%s\" must have a sub selection."
                        (name (:field selection))
                        (name (get-in selection [:field-definition :type])))
       :locations [(:location selection)]}])))

(defn ^:private validate-fragment
  "Validates fragment once to avoid validating it separately for
   each selection."
  [compiled-schema [fragment-name fragment]]
  (validate-selection compiled-schema fragment))

(defn scalar-leafs
  "A GraphQL query is valid only if all leaf nodes (fields without
  sub selections) are of scalar or enum types."
  [compiled-schema query-map]
  (let [selections (:selections query-map)
        fragments (:fragments query-map)]
    (concat
      (mapcat (partial validate-fragment compiled-schema) fragments)
      (mapcat (partial validate-selection compiled-schema) selections))))
