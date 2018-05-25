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
            [com.walmartlabs.lacinia.util :as util]))

(deftest attach-resolvers
  (let [schema {:objects
                {:person
                 {:fields
                  {:id {:type 'Int}
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
                           {:id {:type 'Int}
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
