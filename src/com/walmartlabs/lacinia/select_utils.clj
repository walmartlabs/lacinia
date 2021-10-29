; Copyright (c) 2021-present Walmart, Inc.
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

(ns ^:no-doc com.walmartlabs.lacinia.select-utils
  (:require [com.walmartlabs.lacinia.internal-utils :refer [cond-let remove-vals]]))

(defrecord ResultTuple [alias value])

(defn is-result-tuple?
  [x]
  (instance? ResultTuple x))


(defn ^:private ex-info-map
  [selection path]
  (remove-vals nil? {:locations [(:location selection)]
                     :path path
                     :arguments (:reportable-arguments selection)}))

(defn ^:private assert-error-map
  "An error returned by a resolver should be nil,or a map
  containing at least a :message key with a string value.

  Returns nil, or the map if valid, or throws an exception."
  [error-map]
  (cond
    (nil? error-map)
    nil

    (string? (:message error-map))
    error-map

    :else
    (throw (ex-info (str
                      "Errors must be nil, or a map containing, at minimum, a :message key with a string value.")
             {:error error-map}))))

(defn ^:private structured-error-map
  "Converts an error map and extra data about location, path, etc. into the
  correct format:  top level keys :message, :path, and :location, and anything else
  under a :extensions key."
  [error-map extra-data]
  (let [{:keys [message extensions]} error-map
        {:keys [locations path]} extra-data
        extensions' (merge (dissoc error-map :message :extensions)
                      (dissoc extra-data :locations :path)
                      extensions)]
    (cond-> {:message message
             :locations locations
             :path path}
      (seq extensions') (assoc :extensions extensions'))))

(defn enhance-error
  "From an error map, or a collection of error maps, add additional data to
  each error, including location and arguments.  Returns a seq of error maps."
  [selection path error-map]
  (when-let [error-map' (assert-error-map error-map)]
    (let [extra-data (ex-info-map selection path)]
      (structured-error-map error-map' extra-data))))

(defn apply-error
  [execution-context selection path atom-key error-map]
  (when-let [error-map' (enhance-error selection path error-map)]
    (swap! (get execution-context atom-key) conj error-map'))
  execution-context)

(defrecord WrappedValue [value behavior data])

(defn is-wrapped-value?
  [value]
  (instance? WrappedValue value))

(def wrap-value ->WrappedValue)

(defn apply-wrapped-value
  "Modifies the execution context based on the behavior and data in the wrapped value."
  [execution-context selection path {:keys [behavior data]}]
  ;; data is different for each behavior
  (case behavior
    ;; data is an error map to add
    :error (apply-error execution-context selection path :*errors data)

    ;; data is a map of values to merge into the context, consumed by resolves further
    ;; down (closer to the leaves).
    :context (update execution-context :context merge data)

    :extensions (let [[f args] data
                      *extensions (:*extensions execution-context)]
                  (apply swap! *extensions f args)
                  execution-context)

    ;; data is an error map to be added to the warnings
    :warning (apply-error execution-context selection path :*warnings data)))
