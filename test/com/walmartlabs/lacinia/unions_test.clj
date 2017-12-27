(ns com.walmartlabs.lacinia.unions-test
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.walmartlabs.lacinia.schema :refer [compile tag-with-type]]
    [com.walmartlabs.lacinia :as ql]
    [com.walmartlabs.test-utils :refer [is-thrown]]
    [com.walmartlabs.test-reporting :refer [report]]))

(def base-schema
  {:objects
   {:business
    {:fields
     {:id {:type :ID}
      :name {:type :String}}}
    :employee
    {:fields {:id {:type :ID}
              :employer {:type :business}
              :given_name {:type :String}
              :family_name {:type :String}}}}
   :unions
   {:searchable
    {:members [:business :employee]}}
   :queries
   {:businesses
    {:type '(list :business)
     :resolve identity}
    :search
    {:type '(list :searchable)
     :resolve identity}}})

(def example-business {:id "1000"
                       :name "General Products"})

(def example-employee {:id "2000"
                       :given_name "Louis"
                       :family_name "Wu"
                       :employer example-business})

(defn resolve-search
  "Simple version, doesn't apply type metadata."
  [_ _ _]
  [example-business example-employee])

(defn resolve-search+
  [_ _ _]
  [(tag-with-type example-business :business)
   (tag-with-type example-employee :employee)])

(defn execute [compiled-schema q]
  (ql/execute compiled-schema q nil nil))

(deftest union-references-unknown
  ;; This covers everything: listing either completely unknown object types,
  ;; to listing unions, enums, or interface types.
  (let [schema (merge base-schema
                      {:unions {:searchable {:members [:business :account]}}})]
    (is-thrown [t (compile schema)]
      (is (= "Union `searchable' references unknown type `account'."
             (.getMessage t)))
      (is (= :searchable
             (-> t ex-data :union :type-name))))))

(deftest requires-type-metadata-be-added-for-union-resolves
  (let [schema (-> base-schema
                   (assoc-in [:queries :search :resolve] resolve-search)
                   compile)
        q "{ search {
    ... on business { id name }
    ... on employee { id family_name }}}"
        result (execute schema q)]
    (report result
            (is (= "Field resolver returned an instance not tagged with a schema type."
                   (-> result :errors first :message))))))

(deftest resolves-using-concrete-type-in-spread
  (let [schema (-> base-schema
                   (assoc-in [:queries :search :resolve] resolve-search+)
                   compile)
        q "{ search {
             ... on searchable {
                ... on business { id name }
                ... on employee { id family_name }}}}"
        result (execute schema q)]
    (is (= {:data {:search [{:id "1000" :name "General Products"}
                            {:id "2000" :family_name "Wu"}]}}
           result))))

(defrecord Business [id name])
(defrecord Employee [id given_name family_name employer])

(defn resolve-search-types
  [_ _ _]
  [(map->Business example-business)
   (map->Employee example-employee)])

(deftest resolve-via-object-tag
  (let [schema (-> base-schema
                   (assoc-in [:objects :business :tag] Business)
                   (assoc-in [:objects :employee :tag] 'com.walmartlabs.lacinia.unions_test.Employee)
                   (assoc-in [:queries :search :resolve] resolve-search-types)
                   compile)
        q "{ search {
             ... on searchable {
                ... on business { id name }
                ... on employee { id family_name }}}}"
        result (execute schema q)]
    (is (= {:data {:search [{:id "1000" :name "General Products"}
                            {:id "2000" :family_name "Wu"}]}}
           result))))


