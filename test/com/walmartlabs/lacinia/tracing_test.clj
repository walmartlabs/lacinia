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

(ns com.walmartlabs.lacinia.tracing-test
  "Tests for the optional tracing logic."
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.test-reporting :refer [reporting]]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.test-utils :refer [simplify]]
    [com.walmartlabs.lacinia :refer [execute]]
    [com.walmartlabs.lacinia.tracing :as tracing]
    [com.walmartlabs.lacinia.schema :as schema]))

(def ^:private enable-timing  (tracing/enable-tracing nil))

;; Used to convert nanos to millis
(def ^:private million (Math/pow 10 6))

(defn ^:private resolve-fast
  [_ args _]
  {:simple (:value args)
   ::slow {:simple (:nested_value args)}
   ::delay (:delay args)})

(defn ^:private resolve-slow
  [_ _ value]
  (let [resolved-value (resolve/resolve-promise)
        f (fn []
            (Thread/sleep (::delay value))
            (resolve/deliver! resolved-value (::slow value)))
        thread (Thread. ^Runnable f)]
    (.start thread)
    resolved-value))

(def ^:private compiled-schema
  (-> (io/resource "timing-schema.edn")
      slurp
      edn/read-string
      (util/attach-resolvers {:resolve-fast resolve-fast
                              :resolve-slow resolve-slow})
      schema/compile))

(defn ^:private q
  ([query]
   (q query nil))
  ([query context]
   (-> (execute compiled-schema query nil context)
       simplify)))

(defn ^:private timing-for
  [result path]
  (->> (get-in result [:extensions :tracing :execution :resolvers])
       (filter #(= path (:path %)))
       first))

(deftest timings-are-off-by-default
  (is (= {:data {:root {:simple "fast!"
                        :slow {:simple "slow!!"}}}}
         (q "{ root(delay: 50) { simple slow { simple }}}"))))

(deftest timing-is-collected-when-enabled
  (let [result (q "{ root(delay: 50) { simple slow { simple }}}" enable-timing)]
    (is (seq (get-in result [:extensions :tracing]))
        "Some timings were collected.")))

(deftest collects-timing-for-provided-resolvers
  (doseq [delay [25 50 75]
          :let [result (q (str "{ root(delay: " delay ") { slow { simple }}}") enable-timing)
                slow-timing (timing-for result [:root :slow])
                {:keys [parentType fieldName returnType duration]} slow-timing]]
    (reporting result
               (is (= parentType :Fast))
               (is (= fieldName :slow))
               (is (= returnType "Slow"))
               ;; Allow for a bit of overhead; Thread/sleep is quite inexact.
               ;; Also, the duration is in nanoseconds, but the delay is in millis
               (is (<= delay (/ duration million) (* delay 10))))))

(deftest collects-timing-for-each-execution
  (let [result (q "{ hare: root(delay: 5) { slow { simple }}
                     tortoise: root(delay: 50) { slow { simple }}
                   }"
                  enable-timing)]
    (reporting result
               (let [durations (->> (get-in result [:extensions :tracing :execution :resolvers])
                                    (mapv :duration))]
                 (is (= 6 (count durations)))))))

