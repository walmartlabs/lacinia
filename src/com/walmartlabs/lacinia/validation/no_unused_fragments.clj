(ns com.walmartlabs.lacinia.validation.no-unused-fragments
  (:require [clojure.set])

(defn ^:private fragment-names-used
  "Returns a sequence of all fragment names
  used in a selection, if present, or empty
  sequence otherwise."
  [sel]
  (let [sub-selections (:selections sel)]
    (concat
     (keep :fragment-name sub-selections)
     (mapcat fragment-names-used sub-selections))))

(defn ^:private all-fragments-used
  "Returns a set of unique fragment names used
  throughout the entire query: selections and
  nested fragments."
  [fragments selections]
  (set (apply concat
              ;; fragment names in selections
              (mapcat fragment-names-used selections)
              ;; fragment names in fragments
              ;; (nested fragments)
              (map fragment-names-used (vals fragments)))))

(defn no-unused-fragments
  "Validates if all fragment definitions are spread
  within operations, or spread within other fragments
  spread within operations."
  [compiled-schema query-map]
  (let [{:keys [fragments selections]} query-map
        f-locations (into {} (map (fn [[f-name {location :location}]]
                                    {f-name location})
                                  fragments))
        f-definitions (set (keys fragments))
        f-names-used (all-fragments-used fragments selections)]
    (for [unused-f-definition (clojure.set/difference f-definitions f-names-used)]
      {:message (format "Fragment \"%s\" is never used."
                        (name unused-f-definition))
       :locations [(unused-f-definition f-locations)]})))
