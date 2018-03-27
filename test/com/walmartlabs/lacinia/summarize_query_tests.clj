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
