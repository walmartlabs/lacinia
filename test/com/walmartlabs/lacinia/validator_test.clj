(ns com.walmartlabs.lacinia.validator-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.test-schema :refer [test-schema]])
  (:import (clojure.lang ExceptionInfo)))

;;-------------------------------------------------------------------------------
;; ## Tests

(def compiled-schema (schema/compile test-schema))

(deftest scalar-leafs-validations
  (testing "All leafs are scalar types or enums"
    (let [q "{ hero }"]
      (is (= {:errors [{:message "Field \"hero\" of type \"character\" must have a sub selection.",
                        :locations [{:line 1, :column 0}]}]}
             (execute compiled-schema q {} nil))))
    (let [q "{ hero { name friends } }"]
      (is (= {:errors [{:message "Field \"friends\" of type \"character\" must have a sub selection.",
                        :locations [{:line 1, :column 7}]}]}
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
      (is (= {:errors [{:message "Field \"friends\" of type \"character\" must have a sub selection.",
                        :locations [{:line 4, :column 23}]}]}
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
      (is (= {:errors [{:message "Field \"friends\" of type \"character\" must have a sub selection.",
                        :locations [{:line 7, :column 25}]}]}
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
      (is (= {:errors [{:message "Field \"forceSide\" of type \"force\" must have a sub selection.",
                        :locations [{:line 2, :column 18}]}
                       {:message "Field \"friends\" of type \"character\" must have a sub selection.",
                        :locations [{:line 5, :column 23}]}
                       {:message "Field \"forceSide\" of type \"force\" must have a sub selection.",
                        :locations [{:line 5, :column 23}]}]}
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
      (is (= {:errors [{:message "Field \"forceSide\" of type \"force\" must have a sub selection.",
                        :locations [{:line 2, :column 18}]}
                       {:message "Field \"friends\" of type \"character\" must have a sub selection.",
                        :locations [{:line 5, :column 23}]}
                       {:message "Field \"members\" of type \"character\" must have a sub selection.",
                        :locations [{:line 9, :column 27}]}]}
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
      (is (= {:errors [{:message "Field \"forceSide\" of type \"force\" must have a sub selection."
                        :locations [{:line 2, :column 18}]}
                       {:message "Field \"friends\" of type \"character\" must have a sub selection.",
                        :locations [{:line 5, :column 23}]}]}
             (execute compiled-schema q {} nil))))
    (let [q "{ hero { name { id } } }"]
      (is (= {:errors [{:message "Path de-references through a scalar type."
                        :locations [{:column 14
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
    (is (= {:errors [{:message "Unknown fragment \"FooFragment\". Fragment definition is missing."
                      :locations [{:line 2 :column 37}]}]}
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
    (is (= {:errors [{:message "Unknown fragment \"FooFragment\". Fragment definition is missing."
                      :locations [{:line 2 :column 37}]}
                     {:message "Unknown fragment \"BarFragment\". Fragment definition is missing."
                      :locations [{:line 5 :column 37}]}
                     {:message "Fragment \"HumanFragment\" is never used.",
                      :locations [{:line 9, :column 11}]}]}
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
    (is (= {:errors [{:message "Unknown fragment \"appearsInFragment\". Fragment definition is missing."
                      :locations [{:line 8 :column 50}]}]}
           (execute compiled-schema q {} nil)))))

(deftest fragments-on-composite-types-validation
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               ... on String {
                 name
               }
             }
           }"]
    (is (= {:errors [{:locations [{:column 37
                                   :line 2}]
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
                      :locations [{:column 45
                                   :line 6}]
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
    (is (= {:errors [{:message "Fragment \"appearsInFragment\" is never used.",
                      :locations [{:line 12, :column 11}]}]}
           (execute compiled-schema q {} nil)))))

(deftest query-argument-validations
    (let [q "{ echoArgs(integer: \"hello world\") { integer } }"]
      (is (= {:errors [{:argument :integer
                        :field :echoArgs
                      :locations [{:column 0
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
                        :locations [{:column 0
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
      (is (= {:errors [{:argument :inputObject
                        :field :echoArgs
                        :locations [{:column 0
                                     :line 1}]
                        :message "Exception applying arguments to field `echoArgs': For argument `inputObject', a single argument value was provided for a list argument."
                        :query-path []}]}
             (execute compiled-schema q {} nil)))))

  (testing "invalid array element"
    (let [q "{echoArgs(integer: 3, integerArray: [1, 2, \"foo\"]) { integer }}"]
      (is (= {:errors [{:argument :integerArray
                        :field :echoArgs
                        :locations [{:column 0
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
                        :locations [{:column 9
                                     :line 1}]
                        :message "Exception applying arguments to field `addHeroEpisodes': Not all non-nullable arguments have supplied values."
                        :missing-arguments [:id]
                        :query-path []}]}
             (execute compiled-schema q nil nil))))
    (let [q "mutation { addHeroEpisodes(id:\"1004\") { name appears_in } }"]
      (is (= {:errors [{:field :addHeroEpisodes
                        :locations [{:column 9
                                     :line 1}]
                        :message "Exception applying arguments to field `addHeroEpisodes': Not all non-nullable arguments have supplied values."
                        :missing-arguments [:episodes]
                        :query-path []}]}
             (execute compiled-schema q nil nil))))
    (let [q "mutation { addHeroEpisodes { name appears_in } }"]
      (is (= {:errors [{:field :addHeroEpisodes
                        :locations [{:column 9
                                     :line 1}]
                        :message "Exception applying arguments to field `addHeroEpisodes': Not all non-nullable arguments have supplied values."
                        :missing-arguments [:episodes :id]
                        :query-path []}]}
             (execute compiled-schema q nil nil))))))

(deftest invalid-type-for-query
  (let [e (is (thrown? Throwable
                       (schema/compile {:queries {:unknown_type {:type :not_defined}}})))
        data (ex-data e)]
    (is (= "Field `unknown_type' in type `QueryRoot' references unknown type `not_defined'." (.getMessage e)))
    (is (= {:field {:args nil
                    :qualified-field-name :QueryRoot/unknown_type
                    :field-name :unknown_type
                    :type {:kind :root
                           :type :not_defined}}
            :field-name :unknown_type
            :object-type :QueryRoot
            :schema-types {:object [:MutationRoot
                                    :QueryRoot
                                    :SubscriptionRoot]
                           :scalar [:Boolean
                                    :Float
                                    :ID
                                    :Int
                                    :String]}}
           data))))
