(ns com.walmartlabs.lacinia.validation.fragment-names)

(defn ^:private fragment-defined?
  "Returns empty sequence if a fragment spread is defined
  in fragment definitions.
  Otherwise returns a vector with an error."
  [fragment-defs fragment-spread]
  (if (contains? fragment-defs
                 (:fragment-name fragment-spread))
    []
    [{:message (format "Unknown fragment \"%s\". Fragment definition is missing."
                       (name (:fragment-name fragment-spread)))
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
  [_ query-map]
  (let [fragments (:fragments query-map)
        selections (:selections query-map)]
    (concat
     ;; Validate nested fragments
     (mapcat (fn [[f-name f-definition]]
               (validate-fragments fragments f-definition)) fragments)
     ;; Validate fragments in selections
     (mapcat (partial validate-fragments fragments) selections))))
