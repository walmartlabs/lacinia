;; Copyright (c) 2021-present Walmart, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License")
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns ^:no-doc com.walmartlabs.lacinia.resolve-utils
  (:require [com.walmartlabs.lacinia.resolve :as resolve]
            [com.walmartlabs.lacinia.internal-utils :refer [cond-let]])
  (:import (com.walmartlabs.lacinia.resolve ResolverResultImpl)))

(defn aggregate-results
  "Combines a seq of ResolverResults into a single ResolverResult(Promise) that resolves
   to a seq of values.

   An optional transform function, fx, is passed the resolved seq of values.
   The default xf is identity."
  ([resolver-results]
   (aggregate-results resolver-results identity))
  ([resolver-results xf]
   (cond-let
     :let [results (if (vector? resolver-results)
                     resolver-results
                     (vec resolver-results))
           n (count results)]

     (= 0 n)
     (resolve/resolve-as (xf []))

     :let [solo (when (= 1 n)
                  (first results))]

     (and solo
       (instance? ResolverResultImpl solo))
     (resolve/resolve-as (xf [(:resolved-value solo)]))

     :let [aggregate (resolve/resolve-promise)]

     solo
     (do
       (resolve/on-deliver! solo (fn [value]
                                   (resolve/deliver! aggregate (xf [value]))))
       aggregate)

     :let [buffer (object-array n)
           *wait-count (atom 1)
           _ (loop [i 0]
               (when (< i n)
                 (let [result (get results i)]
                   (if (instance? ResolverResultImpl result)
                     (aset buffer i (:resolved-value result))
                     (do
                       (swap! *wait-count inc)
                       (resolve/on-deliver! result
                         (fn [value]
                           (aset buffer i value)
                           (when (zero? (swap! *wait-count dec))
                             (resolve/deliver! aggregate (xf (vec buffer)))))))))
                 (recur (inc i))))]

     ;; Started count at 1, if it dec's to 0 now, that means all the
     ;; ResolverResults were immediate (not promises)
     (zero? (swap! *wait-count dec))
     (resolve/resolve-as (xf (vec buffer)))

     :else
     aggregate)))

(defn transform-result
  "Passes the resolved value of an existing ResolverResult through a transforming
   function, resulting in a new ResolverResult.

   Optimizes the case for a ResolverResult (pre-realized) vs.
   a ResolverResultPromise."
  [resolver-result xf]
  (if (instance? ResolverResultImpl resolver-result)
    (resolve/resolve-as (-> resolver-result :resolved-value xf))
    (let [xformed (resolve/resolve-promise)]
      (resolve/on-deliver! resolver-result
        (fn [value]
          (resolve/deliver! xformed (xf value))))
      xformed)))
