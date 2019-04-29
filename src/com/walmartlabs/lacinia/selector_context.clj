; Copyright (c) 2019-present Walmart, Inc.
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
(ns ^:no-doc com.walmartlabs.lacinia.selector-context
  "Some code factored out of executor and schema."
  (:require
    [com.walmartlabs.lacinia.resolve :as resolve]))

(defrecord SelectorContext [execution-context callback resolved-value resolved-type])

(defn apply-wrapped-value
  "Modifies the selection context based on the behavior and data in the wrapped value."
  [selection-context {:keys [behavior data]}]
  ;; data is different for each behavior
  (case behavior
    ;; data is an error map to add
    ::resolve/error (update selection-context :errors conj data)

    ;; data is a map of values to merge into the context, consumed by resolves further
    ;; down (closer to the leaves).
    ::resolve/context (update-in selection-context [:execution-context :context] merge data)

    ::resolve/extensions (let [[f args] data
                               *extensions (get-in selection-context [:execution-context :*extensions])]
                           (apply swap! *extensions f args)
                           selection-context)

    ;; data is an error map to be added to the warnings
    ::resolve/warning (update selection-context :warnings conj data)))

