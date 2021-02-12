(ns com.walmartlabs.lacinia.select-type-test
  (:require [clojure.test :refer [deftest is]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.test-utils :refer [compile-schema]]
            [com.walmartlabs.lacinia.selection :as sel]))


(def ^:private schema
  (compile-schema "select-type-schema.edn" {}))

(deftest not-a-type-returns-nil
  (is (nil?
        (schema/select-type schema :DoesNotExist))))

(deftest basic-type
  (let [order (schema/select-type schema :Order)]
    (is (some? order))
    (is (= :object (sel/type-kind order)))
    (is (= #{:id :items}
           (-> order
               sel/fields
               keys
               set)))))

(deftest can-navigate-to-field-type
  (let [items (-> (schema/select-type schema :Order)
            sel/fields
            :items
            sel/root-type)]
    (is (some? items))
    (is (= :object (sel/type-kind items)))))

(deftest can-navigate-to-argument-type
  (let [argument (-> (schema/select-type schema :Query)
                     sel/fields
                     :order
                     sel/argument-defs
                     :id)]
    (is (= :Query/order.id
           (sel/qualified-name argument)))
    (is (= :String (sel/root-type-name argument)))
    (is (= :scalar
           (-> argument
               sel/root-type
               sel/type-kind)))))
