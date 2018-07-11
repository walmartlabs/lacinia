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

(ns com.walmartlabs.lacinia.validator-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.test-schema :refer [test-schema]]))

;;-------------------------------------------------------------------------------
;; ## Tests

(def compiled-schema (schema/compile test-schema))

(deftest scalar-leafs-validations
  (testing "All leafs are scalar types or enums"
    (let [q "{ hero }"]
      (is (= {:errors [{:message "Field `hero' (of type `character') must have at least one selection.",
                        :locations [{:line 1
                                     :column 3}]}]}
             (execute compiled-schema q {} nil))))
    (let [q "{ hero { name friends } }"]
      (is (= {:errors [{:message "Field `friends' (of type `character') must have at least one selection.",
                        :locations [{:line 1
                                     :column 15}]}]}
             (execute compiled-schema q {} nil))))
    (let [q "query NestedQuery {
             hero {
               name
               friends {
                 name
                 appears_in
                 friends
               }
             }
            }"]
      (is (= {:errors [{:message "Field `friends' (of type `character') must have at least one selection.",
                        :locations [{:column 18
                                     :line 7}]}]}
             (execute compiled-schema q {} nil))))
    (let [q "query NestedQuery {
             hero {
               name
               friends {
                 name
                 appears_in
                 friends { name
                           friends
                 }
               }
             }
            }"]
      (is (= {:errors [{:message "Field `friends' (of type `character') must have at least one selection."
                        :locations [{:column 28
                                     :line 8}]}]}
             (execute compiled-schema q {} nil))))
    (let [q "query NestedQuery {
             hero {
               name
               forceSide
               friends {
                 friends
                 name
                 appears_in
                 forceSide
               }
             }
            }"]
      (is (= {:errors [{:locations [{:column 16
                                     :line 4}]
                        :message "Field `forceSide' (of type `force') must have at least one selection."}
                       {:locations [{:column 18
                                     :line 6}]
                        :message "Field `friends' (of type `character') must have at least one selection."}
                       {:locations [{:column 18
                                     :line 9}]
                        :message "Field `forceSide' (of type `force') must have at least one selection."}]}
             (execute compiled-schema q {} nil))))
    (let [q "query NestedQuery {
             hero {
               name
               forceSide
               friends {
                 friends
                 name
                 appears_in
                 forceSide { name
                             members
                 }
               }
             }
            }"]
      (is (= {:errors [{:locations [{:column 16
                                     :line 4}]
                        :message "Field `forceSide' (of type `force') must have at least one selection."}
                       {:locations [{:column 18
                                     :line 6}]
                        :message "Field `friends' (of type `character') must have at least one selection."}
                       {:locations [{:column 30
                                     :line 10}]
                        :message "Field `members' (of type `character') must have at least one selection."}]}
             (execute compiled-schema q {} nil))))
    (let [q "query NestedQuery {
             hero {
               name
               forceSide
               friends {
                 friends
                 name
                 appears_in
                 forceSide { name
                             members { name }
                 }
               }
             }
            }"]
      (is (= {:errors [{:locations [{:column 16
                                     :line 4}]
                        :message "Field `forceSide' (of type `force') must have at least one selection."}
                       {:locations [{:column 18
                                     :line 6}]
                        :message "Field `friends' (of type `character') must have at least one selection."}]}
             (execute compiled-schema q {} nil))))
    (let [q "{ hero { name { id } } }"]
      (is (= {:errors [{:message "Path de-references through a scalar type."
                        :locations [{:column 17
                                     :line 1}]
                        :field :id
                        :query-path [:hero :name]}]}
             (execute compiled-schema q {} nil))))))

(deftest fragment-names-validations
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               ...HumanFragment
             }
             leia: human(id: \"1003\") {
               ...HumanFragment
             }
           }
           fragment HumanFragment on human {
             name
             homePlanet
           }"]
    (is (nil? (:errors (execute compiled-schema q {} nil)))))
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               ...FooFragment
             }
             leia: human(id: \"1003\") {
               ...HumanFragment
             }
           }
           fragment HumanFragment on human {
             name
             homePlanet
           }"]
    (is (= {:errors [{:message "Unknown fragment `FooFragment'. Fragment definition is missing."
                      :locations [{:line 3
                                   :column 19}]}]}
           (execute compiled-schema q {} nil))))
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               ...FooFragment
             }
             leia: human(id: \"1003\") {
               ...BarFragment
             }
           }
           fragment HumanFragment on human {
             name
             homePlanet
           }"]
    (is (= {:errors [{:locations [{:column 19
                                   :line 3}]
                      :message "Unknown fragment `FooFragment'. Fragment definition is missing."}
                     {:locations [{:column 19
                                   :line 6}]
                      :message "Unknown fragment `BarFragment'. Fragment definition is missing."}
                     {:locations [{:column 21
                                   :line 9}]
                      :message "Fragment `HumanFragment' is never used."}]}
           (execute compiled-schema q {} nil))))
  (let [q "query withNestedFragments {
             luke: human(id: \"1000\") {
               friends {
                 ...friendFieldsFragment
               }
             }
           }
           fragment friendFieldsFragment on human {
             id
             name
             ...appearsInFragment
           }
           fragment appearsInFragment on human {
             appears_in
           }"]
    (is (nil? (:errors (execute compiled-schema q {} nil)))))
  (let [q "query withNestedFragments {
             luke: human(id: \"1000\") {
               friends {
                 ...friendFieldsFragment
               }
             }
           }
           fragment friendFieldsFragment on human {
             id
             name
             ...appearsInFragment
           }"]
    (is (= {:errors [{:message "Unknown fragment `appearsInFragment'. Fragment definition is missing."
                      :locations [{:line 11 :column 17}]}]}
           (execute compiled-schema q {} nil)))))

(deftest fragments-on-composite-types-validation
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               ... on String {
                 name
               }
             }
           }"]
    (is (= {:errors [{:locations [{:column 23
                                   :line 3}]
                      :message "Fragment cannot condition on non-composite type `String'."
                      :query-path [:human]}]}
           (execute compiled-schema q {} nil))))
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               ...scalarFragment
             }
           }
           fragment scalarFragment on String {
             id
           }"]
    (is (= {:errors [{:field :id
                      :locations [{:column 14
                                   :line 7}]
                      :message "Path de-references through a scalar type."
                      :query-path [:scalarFragment/String]}]}
           (execute compiled-schema q {} nil)))))

(deftest no-unused-fragments
  (let [q "query withNestedFragments {
             luke: human(id: \"1000\") {
               friends {
                 ...friendFieldsFragment
               }
             }
           }
           fragment friendFieldsFragment on human {
             id
             name
           }
           fragment appearsInFragment on human {
             appears_in
           }"]
    (is (= {:errors [{:message "Fragment `appearsInFragment' is never used.",
                      :locations [{:line 12
                                   :column 21}]}]}
           (execute compiled-schema q {} nil)))))

(deftest query-argument-validations
    (let [q "{ echoArgs(integer: \"hello world\") { integer } }"]
      (is (= {:errors [{:argument :integer
                        :field :echoArgs
                        :locations [{:column 3
                                   :line 1}]
                        :message "Exception applying arguments to field `echoArgs': For argument `integer', scalar value is not parsable as type `Int'."
                        :query-path []
                        :type-name :Int
                      :value "hello world"}]}
             (execute compiled-schema q {} nil))))

  (testing "undefined argument"
    (let [q "{ echoArgs(undefinedArg: 1) { integer } }"]
      (is (= {:errors [{:argument :undefinedArg
                        :defined-arguments [:integer
                                            :integerArray
                                            :inputObject]
                        :field :echoArgs
                        :locations [{:column 3
                                     :line 1}]
                        :message "Exception applying arguments to field `echoArgs': Unknown argument `undefinedArg'."
                        :query-path []}]}
             (execute compiled-schema q {} nil)))))

  (testing "invalid deeply nested input-object property"
    (let [q "{ echoArgs(integer: 1,
                        integerArray: [2, 3],
                        inputObject: {integer: 4,
                                      string: \"five\",
                                      nestedInputObject: {integerArray: \"hello world\",
                                                          date: \"1983-08-13\"}}) { integer }}"]
      (is (= {:errors
              [{:message
                "Exception applying arguments to field `echoArgs': For argument `inputObject', scalar value is not parsable as type `Int'.",
                :query-path []
                :locations [{:line 1
                             :column 3}]
                :field :echoArgs
                :argument :inputObject
                :value "hello world"
                :type-name :Int}]}

             (execute compiled-schema q {} nil)))))

  (testing "valid deeply nested input-object property of type list but of single value"
    (let [q "{ echoArgs(integer: 1,
                        integerArray: [2, 3],
                        inputObject: {integer: 4,
                                      string: \"five\",
                                      nestedInputObject: {integerArray: 6,
                                                          date: \"1983-08-13\"}}) {
                integer,
                inputObject {
                   integer
                   nestedInputObject {
                      integerArray
                   }
                }
              }
            }"]
      (is (= {:data {:echoArgs {:integer 1,
                                :inputObject {:integer 4,
                                              :nestedInputObject {:integerArray [6]}}}}}
             (execute compiled-schema q {} nil)))))

  (testing "invalid array element"
    (let [q "{echoArgs(integer: 3, integerArray: [1, 2, \"foo\"]) { integer }}"]
      (is (= {:errors [{:argument :integerArray
                        :field :echoArgs
                        :locations [{:column 2
                                     :line 1}]
                        :message "Exception applying arguments to field `echoArgs': For argument `integerArray', scalar value is not parsable as type `Int'."
                        :query-path []
                        :type-name :Int
                        :value "foo"}]}
             (execute compiled-schema q {} nil)))))

  (testing "valid arguments"
    (let [q "{ echoArgs(integer: 1,
                        integerArray: [2, 3],
                        inputObject: {integer: 4,
                                      string: \"five\",
                                      nestedInputObject: {integerArray: [6, 7],
                                                          date: \"1983-08-13\"}}) {
                 integer
                 integerArray
                 inputObject {
                   integer
                   string
                   nestedInputObject {
                     integerArray
                     date
                   }
                 }
               }
             }"]
      (is (= {:data {:echoArgs {:integer 1
                                :integerArray [2, 3]
                                :inputObject {:integer 4
                                              :string "five"
                                              :nestedInputObject {:integerArray [6, 7]
                                                                  :date "A long time ago"}}}}}
             (execute compiled-schema q {} nil)))))

  (testing "Non-nullable arguments"
    (let [q "mutation { addHeroEpisodes(episodes: []) { name appears_in } }"]
      (is (= {:errors [{:field :addHeroEpisodes
                        :locations [{:column 12
                                     :line 1}]
                        :message "Exception applying arguments to field `addHeroEpisodes': Not all non-nullable arguments have supplied values."
                        :missing-arguments [:id]
                        :query-path []}]}
             (execute compiled-schema q nil nil))))
    (let [q "mutation { addHeroEpisodes(id:\"1004\") { name appears_in } }"]
      (is (= {:errors [{:field :addHeroEpisodes
                        :locations [{:column 12
                                     :line 1}]
                        :message "Exception applying arguments to field `addHeroEpisodes': Not all non-nullable arguments have supplied values."
                        :missing-arguments [:episodes]
                        :query-path []}]}
             (execute compiled-schema q nil nil))))
    (let [q "mutation { addHeroEpisodes { name appears_in } }"]
      (is (= {:errors [{:field :addHeroEpisodes
                        :locations [{:column 12
                                     :line 1}]
                        :message "Exception applying arguments to field `addHeroEpisodes': Not all non-nullable arguments have supplied values."
                        :missing-arguments [:episodes :id]
                        :query-path []}]}
             (execute compiled-schema q nil nil))))))

(deftest invalid-type-for-query
  (let [e (is (thrown? Throwable
                       (schema/compile {:queries {:unknown_type {:type :not_defined
                                                                 :resolve identity}}})))
        data (ex-data e)]
    (is (= "Field `__Queries/unknown_type' references unknown type `not_defined'." (.getMessage e)))
    (is (= {:field-name :__Queries/unknown_type
            :schema-types {:object [:MutationRoot
                                    :QueryRoot
                                    :SubscriptionRoot]
                           :scalar [:Boolean
                                    :Float
                                    :ID
                                    :Int
                                    :String]}}
           data))))
