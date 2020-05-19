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

(ns com.walmartlabs.lacinia.util-test
  "Utility functions tests."
  (:require [clojure.test :refer [deftest is]]
            [com.walmartlabs.test-utils :refer [expect-exception]]
            [com.walmartlabs.lacinia.util :as util])
  (:import (clojure.lang ExceptionInfo)))

(defn resolve-id
  [_ _ _])

(deftest attach-resolvers
  (let [schema {:objects
                {:person
                 {:fields
                  {:id {:type 'Int
                        :resolve resolve-id}
                   :name {:type 'String
                          :resolve :person-name}
                   :total-credit {:type 'Int
                                  :resolve [:cents :totalCredit]}}}}
                :queries
                {:q1 {:type 'String
                      :resolve :q1}}}
        ;; Note: these are just for testing.  str, identity, and so forth
        ;; are handy constants, but are not field resolvers (which must return
        ;; a ResolverTuple).
        resolvers {:person-name str
                   :q1 identity
                   :cents (fn [name] name)}
        resolved-schema {:objects
                         {:person
                          {:fields
                           {:id {:type 'Int
                                 :resolve resolve-id}
                            :name {:type 'String
                                   :resolve str}
                            :total-credit {:type 'Int
                                           :resolve :totalCredit}}}}
                         :queries
                         {:q1 {:type 'String
                               :resolve identity}}}]
    (is (= resolved-schema (util/attach-resolvers schema resolvers)))))

(deftest attach-scalar-transformers
  (let [schema {:objects
                {:person {:fields {:id {:type 'Int}
                                   :name {:type 'String}}}}

                :scalars
                {:Foo
                 {:parse identity
                  :serialize :foo-serializer}

                 :Bar
                 {:parse :bar-parser
                  :serialize :bar-serializer}}}
        transformers {:foo-serializer str
                      :bar-parser int
                      :bar-serializer double}]
    (is (= {:objects {:person
                      {:fields {:id {:type 'Int}
                                :name {:type 'String}}}}

            :scalars
            {:Foo
             {:parse identity
              :serialize str}

             :Bar
             {:parse int
              :serialize double}}}
           (util/attach-scalar-transformers schema transformers)))))

(deftest inject-resolvers
  (let [schema {:objects
                {:Person {:fields
                          {:pay_grade {:type :String}}}}
                :queries
                {:get_person {}}}]
    (is (= {:objects
            {:Person
             {:fields {:pay_grade {:type :String
                                   :resolve :pay-grade}}}}
            :queries
            {:get_person {:resolve :get-person}}}
           (util/inject-resolvers schema
                                  {:Person/pay_grade :pay-grade
                                   :queries/get_person :get-person})))))

(deftest inject-resolver-not-found
  (when-let [e (is (thrown? ExceptionInfo #"inject error: not found"
                            (util/inject-resolvers {} {:NotFound/my_field :whatever})
                            ))]
    (is (= {:key :NotFound/my_field}
           (ex-data e)))))

(deftest inject-descriptions
  ;; This tests a couple of cases that aren't covered by the
  ;; parser.schema-test namespace.

  (let [schema {:queries
                {:hello
                 {:type :String
                  :args
                  {:name {:type :String}}}}}
        documentation {:queries/hello "HELLO"
                       :queries/hello.name "HELLO.NAME"}]

    (is (= {:queries
            {:hello
             {:type :String
              :description "HELLO"
              :args
              {:name {:type :String
                      :description "HELLO.NAME"}}}}}
           (util/inject-descriptions schema documentation)))))

(deftest can-inject-scalar-description
  (let [schema {:scalars {:Long {}}}
        documentation {:Long "64 bit value"}]
    (is (= {:scalars {:Long {:description "64 bit value"}}}
           (util/inject-descriptions schema documentation)))))

(deftest can-inject-enum-descriptions
  (let [schema {:enums
                ;; Test when the value is a string, keyword, symbol, or a map containing the
                ;; enum value.
                {:Status {:values [:info "warn" "error"
                                   {:enum-value 'fatal
                                    :deprecated "just use error"}]}}}
        schema' (util/inject-descriptions schema {:Status/info "useful information"
                                                  :Status/warn "just ignore"
                                                  :Status/fatal "blew up real good"})]
    (is (= {:Status {:values [{:enum-value :info
                               :description "useful information"}
                              {:enum-value "warn"
                               :description "just ignore"}
                              "error"
                              {:enum-value 'fatal
                               :deprecated "just use error"
                               :description "blew up real good"}]}}
           (:enums schema')))))


(deftest can-inject-enum-transformers
  (is (= {:enums {:Foo {:parse 1}
                  :Bar {:serialize 2}
                  :Baz {:parse 3
                        :serialize 4}}}
         (util/inject-enum-transformers {:enums {:Foo nil
                                                 :Bar nil
                                                 :Baz nil}}
                                        {:Foo {:parse 1}
                                         :Bar {:serialize 2}
                                         :Baz {:parse 3
                                               :serialize 4
                                               :ignored 5}}))))

(deftest inject-enum-transformers-throws-if-not-found
  (expect-exception "Undefined enum when injecting enum transformer."
                    {:enum :Blatt
                     :enums [:Bar
                             :Baz
                             :Foo]}
                    (util/inject-enum-transformers {:enums {:Foo nil
                                                            :Bar nil
                                                            :Baz nil}}
                                                   {:Blatt nil})))
(deftest inject-streamer
  (let [schema '{:subscriptions
                 {:example {:type String}}}]
    (is (= {:subscriptions {:example {:stream ::streamer
                                      :type 'String}}}
           (util/inject-streamers schema
                                  {:subscriptions/example ::streamer})))))

(deftest inject-scalar-xf
  (let [schema '{:scalars {:Temperature {:description "How hot?"}}}]
    (is (= {:scalars {:Temperature {:parse ::parse
                                    :serialize ::serialize
                                    :description "How hot?"}}}
           (util/inject-scalar-transformers schema
                                            {:Temperature {:parse ::parse
                                                           :serialize ::serialize}})))))

(deftest scalar-not-found-on-inject
  (expect-exception "Undefined scalar when injecting scalar transformer"
                    {:scalar :Wilma
                     :scalars [:Barney
                               :Fred]}
                    (util/inject-scalar-transformers {:scalars {:Fred nil
                                                                :Barney nil}}
                                                     {:Wilma nil})))
