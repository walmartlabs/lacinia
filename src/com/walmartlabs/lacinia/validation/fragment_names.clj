(ns com.walmartlabs.lacinia.validation.fragment-names
  {:no-doc true}
  (:require
    [com.walmartlabs.lacinia.internal-utils :refer [q]]))

(defn ^:private fragment-defined?
  "Returns empty sequence if a fragment spread is defined
  in fragment definitions.
  Otherwise returns a vector with an error."
  [fragment-defs fragment-spread]
  (if (contains? fragment-defs
                 (:fragment-name fragment-spread))
    []
    [{:message (format "Unknown fragment %s. Fragment definition is missing."
                       (-> fragment-spread :fragment-name q))
      :locations [(:location fragment-spread)]}]))

(defn ^:private validate-fragments
  "Validates fragment spreads in a selection,
  when present, against a list of fragment definitions.
  Returns a sequence of errors if errors are found.
  Otherwise returns empty sequence."
  [fragment-defs sel]
  ;; Fragment spreads define a fragment name but no nested selections
  ;; (those are inside the fragment definition).  Fields and
  ;; inline fragments do have nested selections.
  (if (:fragment-name sel)
    (fragment-defined? fragment-defs sel)
    (mapcat #(validate-fragments fragment-defs %) (:selections sel))))

(defn known-fragment-names
  "Validates that all `...Fragment` fragment spreads refer
  to fragments defined in the same document.
  Checks fragments nested in fragment definitions, e.g.

  ```
  fragment friendFieldsFragment on Human {
    id
    name
    ...appearsInFragment
  }
  ```

  and all fragments listed in selections."
  [prepared-query]
  (let [fragments (:fragments prepared-query)
        selections (:selections prepared-query)]
    (concat
     ;; Validate nested fragments
     (mapcat (fn [[_ f-definition]]
               (validate-fragments fragments f-definition)) fragments)
     ;; Validate fragments in selections
     (mapcat (partial validate-fragments fragments) selections))))
