; Copyright (c) 2018-present Walmart, Inc.
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
(ns com.walmartlabs.lacinia.optimize-resolve
  (:require
    [criterium.core :refer [quick-bench]]
    [com.walmartlabs.lacinia.resolve :as resolve
     :refer [resolve-as]]))


(defn extract-result
  [result]
  (let [p (promise)]
    (resolve/on-deliver! result p)
    ;; Now, block until it is ready:
    @p))

(defn combine-results-serial
  [results]
  (reduce
    #(resolve/combine-results conj %1 %2)
    (resolve-as [])
    results))

(defn combine-results-parallel
  [results]
  (let [n (count results)]
    (if (zero? n)
      (resolve-as [])
      (let [*remaining (atom n)
            result-array (object-array n)
            output-result (resolve/resolve-promise)]
        (doall
          (map-indexed
            (fn [i result]
              (resolve/on-deliver! result
                                   (fn [value]
                                     (aset result-array i value)
                                     (when (zero? (swap! *remaining dec))
                                       (resolve/deliver! output-result
                                                         (vec result-array))))))
            results))
        output-result))))

(defn make-results
  [n]
  (into []
        (for [i (range n)]
          (resolve/resolve-as i))))

(comment
  (let [n 5000
        results (make-results n)]
    (println "**** START:" n "****")
    (println "Serial:")
    (quick-bench
      (extract-result
        (combine-results-parallel results)))

    (println "Parallel:")
    (quick-bench
      (extract-result
        (combine-results-serial results)))

    nil)

  )
