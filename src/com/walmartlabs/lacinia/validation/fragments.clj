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

(ns com.walmartlabs.lacinia.validation.fragments
  {:no-doc true}
  (:require
    [com.walmartlabs.lacinia.describe :refer [description-for]]
    [com.walmartlabs.lacinia.internal-utils :refer [q seek cond-let]]
    [com.walmartlabs.lacinia.selection :as selection]))

(defn ^:private validate-fragment-spread
  "Returns nil if a fragment spread is defined
  in fragment definitions.
  Otherwise returns a vector with an error."
  [fragment-defs fragment-spread]
  (when-not (contains? fragment-defs (:fragment-name fragment-spread))
    [{:message (format "Unknown fragment %s. Fragment definition is missing."
                       (-> fragment-spread :fragment-name q))
      :locations [(:location fragment-spread)]}]))

(defn ^:private validate-fragments-in-selection
  "Validates fragment spreads in a selection,
  when present, against a list of fragment definitions.
  Returns a sequence of errors if errors are found.
  Otherwise returns empty sequence."
  [fragment-defs sel]
  ;; Fragment spreads define a fragment name but no nested selections
  ;; (those are inside the fragment definition).  Fields and
  ;; inline fragments do have nested selections.
  (cond-let
    (:fragment-name sel)
    (validate-fragment-spread fragment-defs sel)

    :let [sub-selections (:selections sel)]

    (seq sub-selections)
    (mapcat #(validate-fragments-in-selection fragment-defs %) sub-selections)

    :else
    nil))

(defn ^:private references-fragment?
  [fragment-defs fragment-name selection]
  (case (selection/selection-kind selection)
    :field
    (contains? (:nested-fragments selection) fragment-name)

    :named-fragment
    (let [spread-name (:fragment-name selection)
          referred-fragment (get fragment-defs spread-name)]
      (or
        (contains? (:nested-fragments referred-fragment) fragment-name)
        (seek #(references-fragment? fragment-defs fragment-name %)
              (:selections referred-fragment))))

    :inline-fragment
    (seek #(references-fragment? fragment-defs fragment-name %)
          (:selections selection))))

(defn ^:private validate-non-cyclic
  [fragment-defs]
  (keep (fn [[fragment-name frag-def]]
          (when-let [selection (seek #(references-fragment? fragment-defs fragment-name %)
                                     (:selections frag-def))]
            {:message (format "Fragment %s is self-referential via %s, forming a cycle."
                               (q fragment-name)
                              (description-for selection))
             :locations [(:location selection)]}))
    fragment-defs))

(defn validate-fragments
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
               (validate-fragments-in-selection fragments f-definition)) fragments)
      ;; Validate fragments in selections
      (mapcat (partial validate-fragments-in-selection fragments) selections)
      (validate-non-cyclic fragments))))
