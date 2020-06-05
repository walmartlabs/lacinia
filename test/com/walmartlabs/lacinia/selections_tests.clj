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

(ns com.walmartlabs.lacinia.selections-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.parser :as parser]
    [com.walmartlabs.lacinia.executor :as executor]
    [com.walmartlabs.test-utils :refer [compile-schema execute]]
    [clojure.edn :as edn]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia.schema :as schema]))

(def default-schema
  (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver}))

(defn ^:private parse-and-wrap
  ([query]
   (parse-and-wrap default-schema query))
  ([schema query]
   (let [parsed-query (-> (parser/parse-query schema query)
                          (parser/prepare-with-query-variables nil))]
     (executor/parsed-query->context parsed-query))))


(defn ^:private root-selections
  [query]
  (-> query
      parse-and-wrap
      executor/selections-seq))

(defn ^:private root-selections2
  [query]
  (-> query
      parse-and-wrap
      executor/selections-seq2))

(defn ^:private tree
  ([query]
   (tree default-schema query))
  ([schema query]
   (->> query
        (parse-and-wrap schema)
        executor/selections-tree)))

(deftest simple-cases
  (is (= [:__Queries/hero :character/name]
         (root-selections "{ hero { name }}")))

  (is (= [:__Queries/human :human/name :human/homePlanet]
         (root-selections "{ human { name homePlanet }}")))

  (is (= [:__Queries/human
          :human/name :human/friends :human/enemies
          :character/name                                   ; friends
          :character/appears_in
          :character/name]                                  ; enemies
         (root-selections "{ human { name friends { name appears_in } enemies { name }}}"))))

(deftest selections-account-for-directives

  (is (= [:__Queries/human
          :human/name :human/friends
          ;; :human/enemies and nested :character/name omitted
          :character/name
          :character/appears_in]
         (root-selections "{ human { name friends { name appears_in } enemies @skip(if: true) { name }}}")))

  (is (= [:__Queries/human
          :human/friends
          :human/enemies
          :character/name
          :character/appears_in
          :character/name
          :character/appears_in
          ;; Next two are skipped because the enemies/fcharacter fragment is skipped:
          ; :character/name
          ; :character/appears_in
          ]
         (root-selections "
         { human { ... fcharacter
                       friends { ... fcharacter }
                       enemies { ... fcharacter @skip(if:true) }}}

         fragment fcharacter on character { name appears_in }"))))

(deftest multiple-roots
  ;; This only really applies when getting the selections from the parsed query
  ;; rather than normally, from within a resolver function.
  (let [q "
         { human { name }
           r2: droid { id }
         }"]
    (is (= [:__Queries/human
            :__Queries/droid
            :human/name
            :droid/id]
           (root-selections q)))

    (is (= {:__Queries/droid [{:args {:id "2001"}
                               :alias :r2
                               :selections {:droid/id [nil]}}]
            :__Queries/human [{:args {:id "1001"}
                               :selections {:human/name [nil]}}]}
           (tree q)))

    (is (= [{:name :__Queries/human
             :args {:id "1001"}}
            {:name :__Queries/droid
             :alias :r2
             :args {:id "2001"}}
            {:name :human/name}
            {:name :droid/id}]
           (root-selections2 q)))))

(deftest introspection-cases
  (is (= [:__Queries/hero]
         (root-selections "{ hero { __typename }}")))
  (is (= [:__Queries/hero :character/name]
         (root-selections "{ hero { name __typename }}")))
  (is (= {:__Queries/hero
          [{:selections {:character/name [nil]}}]}
         (tree "{ hero { name __typename }}"))))

(deftest mutations
  (is (= [:__Mutations/changeHeroHomePlanet
          :human/name :human/homePlanet]
         (root-selections "mutation { changeHeroHomePlanet (id: \"123\",
            newHomePlanet: \"Venus\") {
            name homePlanet
          }
         }"))))

(deftest inline-fragments
  (is (= [:__Queries/hero
          :character/name
          :human/homePlanet
          :droid/primary_function]
         (root-selections
           "{
              hero {
                name

                ... on human { homePlanet }

                ... on droid { primary_function }
              }
            }"))))

(deftest named-fragments
  (is (= [:__Queries/hero
          :character/name
          :human/homePlanet
          :droid/primary_function]
         (root-selections
           "query {
              hero {
                name

                ... ifHuman
                ... ifDroid
              }
            }

            fragment ifHuman on human { homePlanet }

            fragment ifDroid on droid { primary_function }
            "))))

(deftest is-selected
  (let [context (parse-and-wrap "{ human { name homePlanet }}")]
    (is (executor/selects-field? context
                                 :human/name))
    (is (executor/selects-field? context :human/homePlanet))

    (is (not (executor/selects-field? context :character/name)))))

(deftest basic-tree
  (is (= {:__Queries/human [{:args {:id "1001"}
                             :selections {:human/name [nil]}}]}
         (tree "{ human { name }}")))

  (is (= {:__Queries/droid [{:args {:id "2001"}
                             :selections {:droid/appears_in [{:alias :appears}]
                                          :droid/friends [{:selections {:character/name [nil]}}]
                                          :droid/name [nil]}}]}
         (tree "{ droid { name appears: appears_in friends { name }}}"))))

(deftest directives-in-tree
  (is (= {:__Queries/droid [{:args {:id "2001"}
                             :selections {:droid/appears_in [{:alias :appears}]
                                          :droid/name [nil]}}]}
         (tree "{ droid { name appears: appears_in friends @include(if: false) { name }}}")))

  (is (= {:__Queries/human [{:args {:id "1001"}
                              :selections {:character/appears_in [nil]
                                           :character/name [nil]
                                           :human/enemies [nil]
                                           :human/friends [{:selections #:character{:appears_in [nil]
                                                                                    :name [nil]}}]}}]}
         (tree
           "
         { human { ... fcharacter
                       friends { ... fcharacter }
                       enemies { ... fcharacter @skip(if:true) }}}

         fragment fcharacter on character { name appears_in }"))))


(deftest inline-fragments-are-flattened-in-tree
  (is (= {:__Queries/hero [{:selections {:character/name [nil]
                                         :droid/primary_function [nil]
                                         :human/friends [{:selections {:character/name [nil]}}]
                                         :human/homePlanet [nil]}}]}
         (tree "{
              hero {
                name

                ... on human { homePlanet friends { name } }

                ... on droid { primary_function }
              }
            }"))))

(deftest named-fragments-are-flattened-in-tree
  (is (= {:__Queries/hero
          [{:selections {:character/name [nil]
                         :character/friends [{:selections {:character/name [nil {:alias :character_name}]
                                                           :droid/primary_function [nil]
                                                           :human/homePlanet [nil]}}]}}]}
         (tree "query {
              hero {
              name
                friends {
                  name

                  ... characterDetails
                  ... ifHuman
                  ... ifDroid
                }
              }
            }

            fragment characterDetails on character {
              character_name: name
            }

            fragment ifHuman on human { homePlanet }

            fragment ifDroid on droid { primary_function }
            "))))

(def ^:private selections-schema
  (compile-schema "selections-schema.edn"
                  {:root-resolve
                   (fn [context _ _]
                     {:tree (pr-str (executor/selections-tree context))
                      :detail "<detail>"})}))

(defn ^:private extract-tree
  [query vars]
  (-> (execute selections-schema query vars nil)
      (get-in [:data :root :tree])
      edn/read-string))

(deftest captures-arguments
  (is (= {:Root/detail [{:args {:int_arg 5
                                :string_arg "frodo"}}]
          :Root/tree [nil]}
         (extract-tree "{ root: get_root {
              tree
              detail(int_arg: 5, string_arg: \"frodo\")
            }
          }"
                       nil))))

(deftest tree-with-aliases
  (is (= {:__Queries/get_root
          [{:selections {:Root/detail [{:args {:int_arg 1}}
                                       {:alias :d5
                                        :args {:int_arg 5}}]}}]}
         (tree selections-schema
               "{ get_root {
                    detail(int_arg: 1)
                    d5: detail(int_arg: 5)
                  }
                }"))))

(deftest captures-arguments-from-vars
  ;; Because the tree is prepared, the actual values are visible
  ;; even for variable-driven fields.
  (is (= {:Root/detail [{:args {:int_arg 42
                                :string_arg "samwise"}}]
          :Root/tree [nil]}
         (extract-tree "query($int_var: Int, $string_var: String) {
           root: get_root {
             tree
             detail(int_arg: $int_var, string_arg: $string_var)
           }
         }"
                       {:int_var 42
                        :string_var "samwise"}))))
