; Copyright (c) 2017-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns com.walmartlabs.lacinia.validation.no-unused-fragments
  {:no-doc true}
  (:require
    [clojure.set :as set]
    [com.walmartlabs.lacinia.internal-utils :refer [q]]))

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
  (-> #{}
      (into (mapcat fragment-names-used selections))
      (into (mapcat fragment-names-used (vals fragments)))))

(defn no-unused-fragments
  "Validates if all fragment definitions are spread
  within operations, or spread within other fragments
  spread within operations."
  [prepared-query]
  (let [{:keys [fragments selections]} prepared-query
        f-locations (into {} (map (fn [[f-name {location :location}]]
                                    {f-name location})
                                  fragments))
        f-definitions (set (keys fragments))
        f-names-used (all-fragments-used fragments [{:selections selections}])]
    (for [unused-f-definition (set/difference f-definitions f-names-used)]
      {:message (format "Fragment %s is never used."
                        (q unused-f-definition))
       :locations [(unused-f-definition f-locations)]})))
