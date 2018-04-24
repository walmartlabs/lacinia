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

(ns com.walmartlabs.lacinia.documentation-test
  "Tests that documentation for fields and field args can be inhertited from interfaces."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :as utils]))

(def ^:private schema
  (utils/compile-schema "doc-inheritance-schema.edn" {}))

(defn ^:private q [query]
  (utils/execute schema query))

(deftest field-description-may-inherit-from-interface
  (is (= {:data
          {:ex1
           {:fields
            [{:description "ex1/alpha"
              :name "alpha"}
             {:description "sierra/bravo"
              :name "bravo"}]}
           :ex2
           {:fields
            [{:description "ex2/alpha"
              :name "alpha"}
             {:description "ex2/bravo"
              :name "bravo"}]}}}
         (q "
     { ex1: __type (name: \"ex1\") {

         fields {
           name
           description
         }
       }

       ex2: __type(name: \"ex2\") {
         fields {
           name
           description
         }
       }
     }"))))

(deftest arg-description-may-inherit-from-interface
  (is (= {:data
          {:ex3
           {:fields
            [{:args
              [{:description "ex3/delta"
                :name "delta"}
               {:description "tango/charlie/echo"
                :name "echo"}]}]}}}
         (q "
         { ex3: __type(name: \"ex3\") {
           fields {
             args {
               name
               description
             }
           }
          }
         }"))))
