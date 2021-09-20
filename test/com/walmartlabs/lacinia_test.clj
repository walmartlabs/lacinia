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

(ns com.walmartlabs.lacinia-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.data.json :as json]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.test-schema :refer [test-schema]]
            [com.walmartlabs.test-utils :refer [simplify expect-exception]]))

(def default-schema
  (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver}))

(defn execute
  "Executes the query but reduces ordered maps to normal maps, which makes
  comparisons easier.  Other tests exist to ensure that order is maintained."
  ([schema q vars context]
   (execute schema q vars context nil))
  ([schema q vars context options]
   (-> (lacinia/execute schema q vars context options)
       simplify)))

;; —————————————————————————————————————————————————————————————————————————————
;; ## Tests

(deftest simple-query
  ;; Standard query with explicit `query'
  (let [q "query heroNameQuery { hero { id name } }"]
    (is (= {:data {:hero {:id "2001" :name "R2-D2"}}}
           (execute default-schema q {} nil))))
  (let [q "query { hero { id name } }"]
    (is (= {:data {:hero {:id "2001" :name "R2-D2"}}}
           (execute default-schema q {} nil))))
  ;; We can omit the `query' piece if it's the only selection
  (let [q "{ hero { id name appears_in } }"]
    (is (= {:data {:hero {:id "2001"
                          :name "R2-D2"
                          :appears_in [:NEWHOPE :EMPIRE :JEDI]}}}
           (execute default-schema q {} nil)))
    (is (= (json/write-str (lacinia/execute default-schema q {} nil))
           "{\"data\":{\"hero\":{\"id\":\"2001\",\"name\":\"R2-D2\",\"appears_in\":[\"NEWHOPE\",\"EMPIRE\",\"JEDI\"]}}}")))
  ;; Reordering fields should change ordering in the :data map
  (let [q "{ hero { name appears_in id }}"]
    (is (= (json/write-str (lacinia/execute default-schema q {} nil))
           "{\"data\":{\"hero\":{\"name\":\"R2-D2\",\"appears_in\":[\"NEWHOPE\",\"EMPIRE\",\"JEDI\"],\"id\":\"2001\"}}}")))
  (let [q "{ hero { appears_in name id }}"]
    (is (= (json/write-str (lacinia/execute default-schema q {} nil))
           "{\"data\":{\"hero\":{\"appears_in\":[\"NEWHOPE\",\"EMPIRE\",\"JEDI\"],\"name\":\"R2-D2\",\"id\":\"2001\"}}}"))))

(deftest mutation-query
  (let [q "mutation ($from : String, $to: String) { changeHeroName(from: $from, to: $to) { name } }"]
    (is (= {:data {:changeHeroName {:name "Solo"}}}
           (execute default-schema q {:from "Han Solo"
                                       :to "Solo"}
                    nil)))))

(deftest operation-name
  ;; Standard query with operation name
  (let [q "query heroNameQuery { hero { id name } } query dummyQuery { hero { id } }"]
    (is (= {:data {:hero {:id "2001" :name "R2-D2"}}}
           (execute default-schema q {} nil {:operation-name "heroNameQuery"}))))

  (let [q "query heroNameQuery { hero { id name } } query dummyQuery { hero { id } }"]
    (is (= {:errors [{:extensions {:op-count 2
                                   :operation-name "notAQuery"}
                      :message "Multiple operations provided but no matching name found."}]}
           (execute default-schema q {} nil {:operation-name "notAQuery"}))))

  (let [q "mutation changeHeroNameMutation ($from : String, $to: String) { changeHeroName(from: $from, to: $to) { name } }
           query dummyQuery { hero { id } }"]
    (is (= {:data {:changeHeroName {:name "Solo"}}}
           (execute default-schema q {:from "Han Solo"
                                      :to "Solo"}
                    nil {:operation-name "changeHeroNameMutation"})))))

(deftest null-value-mutation
  (letfn [(reset-value []
            (execute default-schema
                     "mutation { changeHeroHomePlanet (id: \"1003\", newHomePlanet: \"Alderaan\") { homePlanet } }"
                     {}
                     nil))]
    (testing "null literal"
      (let [q "mutation { changeHeroHomePlanet (id: \"1003\", newHomePlanet: null) { name homePlanet } }"]
        (is (= {:data {:changeHeroHomePlanet {:name "Leia Organa" :homePlanet nil}}}
               (execute default-schema q {} nil)))))
    (reset-value)
    (testing "null variable"
      (let [q "mutation ($id : String!, $newHomePlanet : String) { changeHeroHomePlanet (id: $id, newHomePlanet: $newHomePlanet) { name homePlanet } }"]
        (is (= {:data {:changeHeroHomePlanet {:name "Leia Organa" :homePlanet nil}}}
               (execute default-schema q {:id "1003" :newHomePlanet nil} nil)))))
    (reset-value)
    (testing "missing argument (as opposed to null argument value)"
      (let [q "mutation ($id: String!) { changeHeroHomePlanet (id: $id) { name homePlanet } }"]
        (is (= {:data {:changeHeroHomePlanet {:name "Leia Organa" :homePlanet "Alderaan"}}}
               (execute default-schema q {:id "1003"} nil)))))
    (testing "nested object element values"
      (let [q "query { echoArgs (integerArray: [1 null 3], inputObject: {string: \"yes\", nestedInputObject: {integerArray: [4 5 6]}}) { integerArray inputObject } }"]
        (is (= {:data {:echoArgs {:integerArray [1 nil 3] :inputObject (pr-str {:string "yes" :nestedInputObject {:integerArray [4 5 6]}})}}}
               (execute default-schema q {} nil)))))
    (testing "null list/object element values"
      (let [q "query { echoArgs (integerArray: [1 null 3], inputObject: {string: null}) { integerArray inputObject  } }"]
        (is (= {:data {:echoArgs {:integerArray [1 nil 3] :inputObject (pr-str {:string nil})}}}
               (execute default-schema q {} nil)))))
    (testing "null list/object values become null-ish"
      (let [q "query { echoArgs (integerArray: null, inputObject: null) { integerArray inputObject } }"]
        (is (= {:data {:echoArgs {:integerArray []
                                  :inputObject "nil"}}}
               (execute default-schema q {} nil)))))))

(deftest nested-query
  (let [q "query HeroNameAndFriendsQuery {
               hero {
                 id
                 name
                 friends {
                   name
                 }
               }
             }"]
    (is (= {:data {:hero {:id "2001"
                          :name "R2-D2"
                          :friends [{:name "Luke Skywalker"}
                                    {:name "Han Solo"}
                                    {:name "Leia Organa"}]}}}
           (execute default-schema q {} nil))))
  (let [q "query HeroNameAndFriendsQuery {
               hero {
                 id
                 name
                 friends {
                   name
                   id
                 }
               }
             }"]
    (is (= {:data {:hero {:id "2001"
                          :name "R2-D2"
                          :friends [{:name "Luke Skywalker" :id "1000"}
                                    {:name "Han Solo" :id "1002"}
                                    {:name "Leia Organa" :id "1003"}]}}}
           (execute default-schema q {} nil)))))

(deftest recursive-query
  (let [q "query NestedQuery {
             hero {
               name
               friends {
                 name
                 appears_in
                 friends {
                   name
                 }
               }
             }
            }"]
    (is (= {:data {:hero {:name "R2-D2"
                          :friends [{:name "Luke Skywalker"
                                     :appears_in [:NEWHOPE :EMPIRE :JEDI]
                                     :friends [{:name "Han Solo"}
                                               {:name "Leia Organa"}
                                               {:name "C-3PO"}
                                               {:name "R2-D2"}]}
                                    {:name "Han Solo"
                                     :appears_in [:NEWHOPE :EMPIRE :JEDI]
                                     :friends [{:name "Luke Skywalker"}
                                               {:name "Leia Organa"}
                                               {:name "R2-D2"}]}
                                    {:name "Leia Organa"
                                     :appears_in [:NEWHOPE :EMPIRE :JEDI]
                                     :friends [{:name "Luke Skywalker"}
                                               {:name "Han Solo"}
                                               {:name "C-3PO"}
                                               {:name "R2-D2"}]}]}}}
           (execute default-schema q {} nil)))))

(deftest arguments-query
  (let [q "query FetchLukeQuery {
             human(id: \"1000\") {
               name
             }
           }"]
    (is (= {:data {:human {:name "Luke Skywalker"}}}
           (execute default-schema q nil nil))))
  (let [q "query FetchDarkSideQuery {
             human(id: \"1004\") {
               name
               friends {
                 name
               }
             }
           }"]
    (is (= {:data {:human {:name "Wilhuff Tarkin"
                           :friends [{:name "Darth Vader"}]}}}
           (execute default-schema q nil nil))))
  (let [q "mutation { addHeroEpisodes(id: \"1004\", episodes: []) { name appears_in } }"]
    (is (= {:data {:addHeroEpisodes {:name "Wilhuff Tarkin" :appears_in [:NEWHOPE]}}}
           (execute default-schema q nil nil)))))

(deftest enum-query
  (let [q "mutation { addHeroEpisodes(id: \"1004\", episodes: [JEDI]) { name appears_in } }"]
    (is (= {:data {:addHeroEpisodes {:appears_in [:NEWHOPE
                                                  :JEDI]
                                     :name "Wilhuff Tarkin"}}}
           (execute default-schema q nil nil)))))

(deftest not-found-query
  (let [q "{droid(id: \"non-existent\") {name friends{name}}}"]
    (is (= {:data {:droid nil}}
           (execute default-schema q nil nil)))))

(deftest variable-query
  (let [q "query FetchSomeIDQuery($someId: String!) {
             human(id: $someId) {
               name
             }
           }"
        luke-id "1000"
        han-id "1002"]
    (is (= {:data {:human {:name "Luke Skywalker"}}}
           (execute default-schema q {:someId luke-id} nil)))
    (is (= {:data {:human {:name "Han Solo"}}}
           (execute default-schema q {:someId han-id} nil)))
    (is (= {:errors [{:extensions {:argument :Query/human.id
                                   :variable-name :someId
                                   :field-name :Query/human}
                      :locations [{:column 14
                                   :line 2}]
                      :message "No value was provided for variable `someId', which is non-nullable."}]}
           (execute default-schema q {} nil)))))

(deftest aliased-query
  (let [q "query FetchLukeAliased {
             luke: human(id: \"1000\") {
               name
             }
           }"]
    (is (= {:data {:luke {:name "Luke Skywalker"}}}
           (execute default-schema q nil nil)))))

(deftest double-aliased-query
  (let [q "query FetchLukeAndLeiaAliased {
             luke: human(id: \"1000\") {
               name
             }
             leia: human(id: \"1003\") {
               name
             }
           }"]
    (is (= {:data {:luke {:name "Luke Skywalker"}
                   :leia {:name "Leia Organa"}}}
           (execute default-schema q nil nil)))))

(deftest aliases-on-nested-fields
  (let [q "query { human (id: \"1000\") { buddies: friends { handle: name }}}"
        query-result (execute default-schema q nil nil)]
    (is (= {:data {:human {:buddies [{:handle "Han Solo"}
                                     {:handle "Leia Organa"}
                                     {:handle "C-3PO"}
                                     {:handle "R2-D2"}]}}}
           query-result))))

(deftest duplicated-query
  (let [q "query DuplicateFields {
             luke: human(id: \"1000\") {
               name
               homePlanet
             }
             leia: human(id: \"1003\") {
               name
               homePlanet
             }
           }"]
    (is (= {:data {:luke {:name "Luke Skywalker"
                          :homePlanet "Tatooine"}
                   :leia {:name "Leia Organa"
                          :homePlanet "Alderaan"}}}
           (execute default-schema q nil nil)))))

(deftest fragmented-query
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
    (is (= {:data {:luke {:name "Luke Skywalker"
                          :homePlanet "Tatooine"}
                   :leia {:name "Leia Organa"
                          :homePlanet "Alderaan"}}}
           (execute default-schema q nil nil))))
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               ... on human {
                 name
                 homePlanet
               }
             }
             leia: human(id: \"1003\") {
               ... on human {
                 name
                 homePlanet
               }
             }
           }"]
    (is (= {:data {:luke {:name "Luke Skywalker"
                          :homePlanet "Tatooine"}
                   :leia {:name "Leia Organa"
                          :homePlanet "Alderaan"}}}
           (execute default-schema q nil nil))))
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               id
               ... on human {
                 name
                 homePlanet
               }
             }
             leia: human(id: \"1003\") {
               ... on human {
                 name
                 homePlanet
                 ...appearsInFragment
               }
             }
           }
           fragment appearsInFragment on human {
             appears_in
           }"]
    (is (= {:data {:luke {:name "Luke Skywalker"
                          :id "1000"
                          :homePlanet "Tatooine"}
                   :leia {:name "Leia Organa"
                          :homePlanet "Alderaan"
                          :appears_in [:NEWHOPE :EMPIRE :JEDI]}}}
           (execute default-schema q nil nil))))
  (let [q "query UseFragment {
             luke: human(id: \"1000\") {
               ...appearsInFragment
               ... on human {
                 name
                 homePlanet
               }
             }
             leia: human(id: \"1003\") {
               ...appearsInFragment
             }
           }
           fragment appearsInFragment on human {
             appears_in
           }"]
    (is (= {:data {:luke {:name "Luke Skywalker"
                          :homePlanet "Tatooine"
                          :appears_in [:NEWHOPE :EMPIRE :JEDI]}
                   :leia {:appears_in [:NEWHOPE :EMPIRE :JEDI]}}}
           (execute default-schema q nil nil))))
  (let [q "query InvalidInlineFragment {
             human(id: \"1001\") {
               ... on foo {
                 name
               }
             }
            }"]
    (is (= {:errors [{:message "Inline fragment has a type condition on unknown type `foo'."
                      :locations [{:column 23
                                   :line 3}]}]}
           (execute default-schema q nil nil)))))

(deftest invalid-query
  (let [q "{ human(id: \"1000\") { name "
        {:keys [errors data]} (execute default-schema q nil nil)]
    (is (empty? data))
    (is (= 1 (count errors)))
    (let [err (-> errors first :message)]
      (is (.contains err "Failed to parse GraphQL query")))))

(deftest resolve-callback-failures
  (let [q "{ droid(id: \"2001\") { accessories }}"
        executed (execute default-schema q nil nil)]
    (is (= {:data {:droid {:accessories nil}}
            :errors [{:path [:droid :accessories]
                      :message "Field resolver returned a single value, expected a collection of values."
                      :locations [{:line 1
                                   :column 23}]}]}
           executed)))
  (let [q "{droid(id: \"2000\") { incept_date }}"
        executed (execute default-schema q nil nil)]
    (is (= {:data {:droid {:incept_date nil}}
            :errors [{:path [:droid :incept_date]
                      :message "Field resolver returned a collection of values, expected only a single value."
                      :locations [{:line 1
                                   :column 22}]}]}
           executed))))

(deftest non-nullable
  ;; human's type is (non-null :character), but is null because the id does not exist.
  ;; This triggers the non-nullable field error.
  (let [q "{ human(id: \"12345678\") { name }}"
        executed (execute default-schema q nil nil)]
    (is (= {:data nil
            :errors [{:extensions {:arguments {:id "12345678"}}
                      :locations [{:column 3
                                   :line 1}]
                      :message "Non-nullable field was null."
                      :path [:human]}]}
           executed)))
  (let [q "{ human(id: \"1000\") { name foo }}"
        executed (execute default-schema q nil nil)]
    (is (= {:data nil
            :errors [{:message "Non-nullable field was null."
                      :path [:human :foo]
                      :locations [{:line 1
                                   :column 28}]}]}
           executed)))
  (testing "field declared as non-nullable resolved to null"
    (let [q "{ hero { foo }}"
          executed (execute default-schema q nil nil)]
      (is (= {:data nil
              :errors [{:message "Non-nullable field was null."
                        :path [:hero :foo]
                        :locations [{:line 1
                                     :column 10}]}]}
             executed)
          "should null the top level when non-nullable field returns null")))
  (testing "field declared as non-nullable resolved to null"
    (let [q "{ hero { arch_enemy { foo } }}"
          executed (execute default-schema q nil nil)]
      (is (= {:data nil
              :errors [{:message "Non-nullable field was null."
                        :path [:hero :arch_enemy]
                        :locations [{:line 1
                                     :column 10}]}]}
             executed)
          "nulls the first nullable object after a field returns null in a chain of fields that are non-null")))
  (testing "nullable list of nullable objects (friends) with non-nullable selections"
    (let [q "{ hero { friends { arch_enemy { foo } } }}"
          executed (execute default-schema q nil nil)]
      (is (= {:data {:hero {:friends [nil nil nil]}}
              :errors [{:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 20}]
                        :path [:hero :friends 0 :arch_enemy]}
                       {:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 20}]
                        :path [:hero :friends 1 :arch_enemy]}
                       {:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 20}]
                        :path [:hero :friends 2 :arch_enemy]}]}
             executed)
          "nulls the first nullable object after a non-nullable field returns null")))
  (testing "nullable list of nullable objects (friends) with nullable selections containing non-nullable field"
    (let [q "{ hero { friends { best_friend { foo } } }}"
          executed (execute default-schema q nil nil)]
      (is (= {:data {:hero {:friends [{:best_friend nil} {:best_friend nil} {:best_friend nil}]}}
              :errors [{:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 34}]
                        :path [:hero :friends 0 :best_friend :foo]}
                       {:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 34}]
                        :path [:hero :friends 1 :best_friend :foo]}
                       {:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 34}]
                        :path [:hero :friends 2 :best_friend :foo]}]}
             executed)
          "nulls the first nullable object after a non-nullable field returns null")))
  (testing "non-nullable list of nullable objects (family) with non-nullable selections"
    (let [q "{ hero { family { arch_enemy { foo } } } }"
          executed (execute default-schema q nil nil)]
      (is (= {:data {:hero {:family [nil nil nil]}}
              :errors [{:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 19}]
                        :path [:hero :family 0 :arch_enemy]}
                       {:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 19}]
                        :path [:hero :family 1 :arch_enemy]}
                       {:message "Non-nullable field was null."
                        :locations [{:line 1
                                     :column 19}]
                        :path [:hero :family 2 :arch_enemy]}]}
             executed)
          "nulls the first nullable object after a non-nullable field returns null"))))

(deftest default-value-test
  (testing "Should use the default-value"
    (let [q "{ droid {
                 name
               }
             }"]
      (is (= {:data
              {:droid
               {:name "R2-D2"}}}
             (execute default-schema q nil nil)))
      (let [q "query UseFragment {
                 threecpo: droid(id: \"2000\") {
                   ...DroidFragment
                 }
                 r2d2: droid {
                   ...DroidFragment
                 }
               }
               fragment DroidFragment on droid {
                 name
                 friends {
                   name
                 }
                 appears_in
               }"]
        (is (= {:data {:threecpo {:name "C-3PO"
                                  :friends [{:name "Luke Skywalker"}
                                            {:name "Han Solo"}
                                            {:name "Leia Organa"}
                                            {:name "R2-D2"}]
                                  :appears_in [:NEWHOPE :EMPIRE :JEDI]}
                       :r2d2 {:name "R2-D2"
                              :friends [{:name "Luke Skywalker"}
                                        {:name "Han Solo"}
                                        {:name "Leia Organa"}]
                              :appears_in [:NEWHOPE :EMPIRE :JEDI]}}}
               (execute default-schema q nil nil))))
      (testing "Should use the value provided by user"
        (let [q "{ droid(id: \"2000\") {
                     name
                   }
                 }"]
          (is (= {:data
                  {:droid
                   {:name "C-3PO"}}}
                 (execute default-schema q nil nil)))))
      (testing "Mutation should use default-value"
        (let [q "mutation ($from : String!) { changeHeroName(from: $from) { name } }"]
          (is (= {:data {:changeHeroName {:name "Rey"}}}
                 (execute default-schema q {:from "Han Solo"}
                          nil)))))
      (testing "Should use the default-value for non-nullable fields"
        (let [q "query UseFragment {
                   vader: human {
                     name
                   }
                 }"]
          (is (= {:data {:vader {:name "Darth Vader"}}}
                 (execute default-schema q nil nil))))))))

(deftest not-allow-not-nullable-with-default-value
  (let [schema-non-nullable-with-defaults
        {:objects
         {:person
          {:fields
           {:id {:type '(non-null String)
                 :default-value "0001"}}}}}]
    (expect-exception
      "Field `person/id' is both non-nullable and has a default value."
      {:field-name :person/id
       :type "String!"}
      (schema/compile schema-non-nullable-with-defaults))))

(deftest allow-single-value-on-list-type-input

  (testing "field argument of list type is a single integer"
    (let [q "{ echoArgs (integerArray: 1) {
                  integerArray
               }
             }"]
      (is (= {:data {:echoArgs {:integerArray [1]}}}
             (execute default-schema q nil nil))
          "should accept single value and coerce it to a list of size one")))

  (testing "field argument of a list type is a single enum"
    (let [q "mutation { addHeroEpisodes(id: \"1004\", episodes: JEDI) { name appears_in } }"]
      (is (= {:data {:addHeroEpisodes {:appears_in [:NEWHOPE
                                                    :JEDI]
                                       :name "Wilhuff Tarkin"}}}
             (execute default-schema q nil nil))
          "should accept single value and coerce it to a list of size one")))

  (testing "field argument of a list type of an input object is a single integer"
    (let [q "{ echoArgs (inputObject: { nestedInputObject: { integerArray: 6 }}) {
                  inputObject
                }
             }"]
      (is (= {:data {:echoArgs {:inputObject (pr-str {:nestedInputObject {:integerArray [6]}})}}}
             (execute default-schema q {} nil))
          "should accept single value and coerce it to a list of size one")))

  (testing "variable of a list type is a single integer"
    (let [q "query QueryWithVariable($intArray: [Int]) {
                 echoArgs(integerArray: $intArray) {
                   integerArray
                }
             }"
          intArray 2]
      (is (= {:data {:echoArgs {:integerArray [2]}}}
             (execute default-schema q {:intArray intArray} nil))
          "should accept single value and coerce it to a list of size one")))

  (testing "field argument of a list type is null"
    (let [q "{ echoArgs (integerArray: null) {
                  integerArray
               }
             }"]
      (is (= {:data {:echoArgs {:integerArray []}}}
             (execute default-schema q nil nil))
          "should coerce null to an empty array"))))
