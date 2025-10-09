(deftest union-supports-leading-vertical-bar
  (let [sdl "union Searchable =\n  | Business\n  | Employee"
        schema (ql/schema sdl)]
    (is (= #{:Business :Employee}
           (set (get-in schema [:unions :Searchable :members])))))
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

(ns com.walmartlabs.lacinia.unions-test
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.walmartlabs.lacinia.schema :refer [compile tag-with-type]]
    [com.walmartlabs.lacinia :as ql]
    [com.walmartlabs.test-utils :refer [expect-exception]]
    [com.walmartlabs.test-reporting :refer [reporting]]))

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
   nil
   (tag-with-type example-employee :employee)])

(defn execute [compiled-schema q]
  (ql/execute compiled-schema q nil nil))

(deftest union-references-unknown
  ;; This covers everything: listing either completely unknown object types,
  ;; to listing unions, enums, or interface types.
  (let [schema (merge base-schema
                      {:unions {:searchable {:members [:business :account]}}})]
    (expect-exception
      "Union `searchable' references unknown type `account'."
      {:schema-types {:object [:Mutation
                               :Query
                               :Subscription
                               :business
                               :employee]
                      :scalar [:Boolean
                               :Float
                               :ID
                               :Int
                               :String]
                      :union [:searchable]}
       :union {:category :union
               :members [:business
                         :account]
               :type-name :searchable}}
      (compile schema))))

(deftest requires-type-metadata-be-added-for-union-resolves
  (let [schema (-> base-schema
                   (assoc-in [:queries :search :resolve] resolve-search)
                   compile)
        q "{ search {
    ... on business { id name }
    ... on employee { id family_name }}}"
        result (execute schema q)]
    (reporting result
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
                            nil
                            {:id "2000" :family_name "Wu"}]}}
           result))))

(defrecord Business [id name])
(defrecord Employee [id given_name family_name employer])

(defn resolve-search-types
  [_ _ _]
  [(map->Business example-business)
   ;; nil is allowed as the type is not explicitly non-null
   nil
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
                            nil
                            {:id "2000" :family_name "Wu"}]}}
           result))))


