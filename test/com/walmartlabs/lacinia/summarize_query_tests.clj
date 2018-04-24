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

(ns com.walmartlabs.lacinia.summarize-query-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [compile-schema]]
    [com.walmartlabs.lacinia.parser :as parser]))

(def ^:private schema (compile-schema "summarize-schema.edn" {:placeholder (constantly nil)}))

(defn ^:private s
  [q]
  (-> (parser/parse-query schema q)
      parser/summarize-query))

(deftest simple-field-names-are-alphabetical
  (is (= "{toy_by_name {aisle name packaging}}"
         (s "{ toy_by_name(name: \"Frisbee\") { name packaging aisle } }")
         )))

(deftest inline-fragments
  (is (= "{product_by_string {__typename aisle cost dealership {city state} model name packaging}}"
         (s "{ product_by_string {
             type: __typename
             ... on Toy { name packaging aisle }
             ... on Truck { model dealership { city state } cost }
           }
         }"))))

(deftest named-fragments
  (is (= "{product_by_string {__typename aisle cost dealership {city postal_code state} model name packaging}}"
         (s "query { product_by_string {
             type: __typename
             ... ToyData
             ... TruckData
           }
         }

         fragment ToyData on Toy { name packaging aisle }
         fragment TruckData on Truck { model dealership { city state postal_code } cost }

         "))))

(deftest multiple-operations
  (is (= "{toy_by_name {name} truck_by_model {model}}"
         (s "{ toy: toy_by_name { name }
               truck: truck_by_model { model }
             }"))))

(deftest multiple-of-same-operation
  (is (= "{toy_by_name {aisle} toy_by_name {name} toy_by_name {packaging}}"
         (s "{ car: toy_by_name { name }
               lego: toy_by_name { packaging }
               doll: toy_by_name { aisle }
             }"))))
