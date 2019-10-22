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

(ns com.walmartlabs.lacinia.timeout-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia :as lacinia]
    [com.walmartlabs.lacinia.schema :as schema]))

(defn create-schema
  [timeout-ms]
  (schema/compile
    {:queries {:wait {:type :String
                      :resolve (fn [_ _ _]
                                 (Thread/sleep timeout-ms)
                                 "ok")}}}))

(def ^:private expected-result {:data {:wait "ok"}})


(defmacro timed
  [& body]
  `(let [start# (System/currentTimeMillis)
         result# (do ~@body)
         elapsed# (- (System/currentTimeMillis) start#)]
     [elapsed# result#]))

(defn execute
  [schema options]
  (lacinia/execute schema "{ wait }" nil nil options))

(deftest no-timeout
  (let [schema (create-schema 100)
        [elapsed result] (timed (execute schema nil))]
    (is (= expected-result result))
    (is (<= 100 elapsed))))

(deftest explicit-timeout
  (let [schema (create-schema 100)
        [elapsed result] (timed (execute schema {:timeout-ms 20}))]
    (is (= {:errors [{:message "Query execution timed out."}]} result))
    ;; Allow for some overhead ...
    (is (<= 20 elapsed 30))))

(deftest explicit-error
  (let [schema (create-schema 100)
        [elapsed result] (timed (execute schema {:timeout-ms 50
                                                 :timeout-error {:message "Too slow!"}}))]
    (is (= {:errors [{:message "Too slow!"}]} result))
    ;; Allow for some overhead ...
    (is (<= 50 elapsed 60))))
