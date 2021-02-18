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
    (is (= :object (sel/type-category order)))
    (is (= #{:id :items}
           (-> order
               sel/fields
               keys
               set)))))

(deftest can-navigate-to-field-type
  (let [order-type (schema/select-type schema :Order)
        items (-> order-type
                  sel/fields
                  :items
                  sel/root-type)]
    (is (= :object (sel/type-category order-type)))
    (is (some? items))
    (is (= :object (sel/type-category items)))))

(deftest root-type-of-simple-field
  (let [item-field (-> (schema/select-type schema :Item)
                       sel/fields
                       :description)]
    (is (= :String (sel/root-type-name item-field)))
    (is (= :String (-> item-field
                       sel/root-type
                       sel/type-name)))))

(deftest root-type-of-simple-argument
  (let [search-argument (-> (schema/select-type schema :Query)
                            sel/fields
                            :order
                            sel/argument-defs
                            :search)]
    (is (= :String (sel/root-type-name search-argument)))
    (is (= :String (-> search-argument
                       sel/root-type
                       sel/type-name)))))

(defn ^:private kind-types [kind]
  (when (some? kind)
    (cons (sel/kind-type kind)
          (kind-types (sel/of-kind kind)))))

(deftest kind-of-field
  (let [order-type (schema/select-type schema :Order)
        items-field (-> order-type sel/fields :items)
        items-kind (sel/kind items-field)]
  (is (= "[Item!]!" (sel/as-type-string items-kind)))
  (is (= [:non-null :list :non-null :root]
         (kind-types items-kind)))
  (is (nil?
        (sel/of-type items-kind)))
  (is (= :Item
        (-> items-kind
            sel/of-kind                                     ; !
            sel/of-kind                                     ; []
            sel/of-kind                                     ; !
            sel/of-type                                     ; Item
            sel/type-name)))))

(deftest kind-of-argument
  (let [id-arg (-> (schema/select-type schema :Query )
                        sel/fields
                        :order
                        sel/argument-defs
                        :id)
        id-kind (sel/kind id-arg)]
    (is (= "String!" (sel/as-type-string id-kind)))

    (is (= :String
           (-> id-kind
               sel/of-kind                                  ; !
               sel/of-type
               sel/type-name)))))

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
               sel/type-category)))))

(deftest can-navigate-to-field-type-of-interface
  (let [container (schema/select-type schema :ItemsContainer)
        items (-> container
                  sel/fields
                  :items
                  sel/root-type)]
    (is (= :interface (sel/type-category container)))
    (is (= :object (sel/type-category items)))))
