(ns com.walmartlabs.introspection-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.test-schema :refer [test-schema]]
            [com.walmartlabs.test-utils :refer [simplify]]))

(def ^:dynamic compiled-schema nil)

(use-fixtures :once
              (fn [f]
                (binding [compiled-schema (schema/compile test-schema)]
                  (f))))


(defn ^:private execute [query]
  (simplify (lacinia/execute compiled-schema query {} nil)))

(deftest simple-introspection-query
  (let [q "{ __type(name: \"human\") { kind name fields { name }}}"]
    (is (= {:data {:__type {:kind "OBJECT"
                            :name "human"
                            :fields
                            (->> compiled-schema :human :fields keys
                                 (map name)
                                 sort
                                 (map #(hash-map :name %)))
                            }}}
           (execute q)))))

(deftest first-level-field-types
  (let [q "{ __type(name: \"human\") {
             kind
             name
             fields {
               name
               type {
                 kind
                 name
               }
             }
           }}"]
    (is (= {:data {:__type {:fields [{:name "appears_in"
                                      :type {:kind "LIST"
                                             :name nil}}
                                     {:name "arch_enemy"
                                      :type {:kind "NON_NULL"
                                             :name nil}}
                                     {:name "bar"
                                      :type {:kind "INTERFACE"
                                             :name "character"}}
                                     {:name "best_friend"
                                      :type {:kind "INTERFACE"
                                             :name "character"}}
                                     {:name "droids"
                                      :type {:kind "NON_NULL"
                                             :name nil}}
                                     {:name "enemies"
                                      :type {:kind "LIST"
                                             :name nil}}
                                     {:name "error_field"
                                      :type {:kind "SCALAR"
                                             :name "String"}}
                                     {:name "family"
                                      :type {:kind "NON_NULL"
                                             :name nil}}
                                     {:name "foo"
                                      :type {:kind "NON_NULL"
                                             :name nil}}
                                     {:name "forceSide"
                                      :type {:kind "OBJECT"
                                             :name "force"}}
                                     {:name "friends"
                                      :type {:kind "LIST"
                                             :name nil}}
                                     {:name "homePlanet"
                                      :type {:kind "SCALAR"
                                             :name "String"}}
                                     {:name "id"
                                      :type {:kind "SCALAR"
                                             :name "String"}}
                                     {:name "multiple_errors_field"
                                      :type {:kind "SCALAR"
                                             :name "String"}}
                                     {:name "name"
                                      :type {:kind "SCALAR"
                                             :name "String"}}
                                     {:name "primary_function"
                                      :type {:kind "LIST"
                                             :name nil}}]
                            :kind "OBJECT"
                            :name "human"}}}
           (execute q)))))

(deftest three-level-type-data
  (let [schema (schema/compile {:objects {:movie {:fields {:release_year {:type 'Int}
                                                           :sequel {:type :movie}
                                                           :title {:type '(non-null String)}
                                                           :actors {:type '(list (non-null String))}}}}})
        q "
        { __type(name: \"movie\") {
          fields {
            name
            type {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                }
              }
            }
          }
        }}"]
    (is (= {:data {:__type {:fields [{:name "actors"
                                      :type {:kind "LIST"
                                             :name nil
                                             :ofType {:kind "NON_NULL"
                                                      :name nil
                                                      :ofType {:kind "SCALAR"
                                                               :name "String"}}}}
                                     {:name "release_year"
                                      :type {:kind "SCALAR"
                                             :name "Int"
                                             :ofType nil}}
                                     {:name "sequel"
                                      :type {:kind "OBJECT"
                                             :name "movie"
                                             :ofType nil}}
                                     {:name "title"
                                      :type {:kind "NON_NULL"
                                             :name nil
                                             :ofType {:kind "SCALAR"
                                                      :name "String"
                                                      :ofType nil}}}]}}}
           (-> (lacinia/execute schema q nil nil)
               simplify)))))

(deftest object-introspection-query
  (let [q "{ __type(name: \"droid\") { kind name interfaces { name }}}"]
    (is (= {:data {:__type {:kind "OBJECT"
                            :name "droid"
                            :interfaces [{:name "character"}]}}}
           (execute q)))))

(deftest recursive-introspection-query
  (let [q "{ __type(name: \"character\") {
             kind
             name
             fields {
               name
               type {
                 kind
                 name
                 fields {
                   name
                   type {
                     kind
                     name
                     ofType { enumValues { name }}
                   }
                 }
               }
             }
           }}"]
    (is (= {:data {:__type {:fields [{:name "appears_in"
                                      :type {:fields []
                                             :kind "LIST"
                                             :name nil}}
                                     {:name "arch_enemy"
                                      :type {:fields []
                                             :kind "NON_NULL"
                                             :name nil}}
                                     {:name "bar"
                                      :type {:fields [{:name "appears_in"
                                                       :type {:kind "LIST"
                                                              :name nil
                                                              :ofType {:enumValues [{:name "NEWHOPE"}
                                                                                    {:name "EMPIRE"}
                                                                                    {:name "JEDI"}]}}}
                                                      {:name "arch_enemy"
                                                       :type {:kind "NON_NULL"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "bar"
                                                       :type {:kind "INTERFACE"
                                                              :name "character"
                                                              :ofType nil}}
                                                      {:name "best_friend"
                                                       :type {:kind "INTERFACE"
                                                              :name "character"
                                                              :ofType nil}}
                                                      {:name "droids"
                                                       :type {:kind "NON_NULL"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "enemies"
                                                       :type {:kind "LIST"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "family"
                                                       :type {:kind "NON_NULL"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "foo"
                                                       :type {:kind "NON_NULL"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "forceSide"
                                                       :type {:kind "OBJECT"
                                                              :name "force"
                                                              :ofType nil}}
                                                      {:name "friends"
                                                       :type {:kind "LIST"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "id"
                                                       :type {:kind "SCALAR"
                                                              :name "String"
                                                              :ofType nil}}
                                                      {:name "name"
                                                       :type {:kind "SCALAR"
                                                              :name "String"
                                                              :ofType nil}}]
                                             :kind "INTERFACE"
                                             :name "character"}}
                                     {:name "best_friend"
                                      :type {:fields [{:name "appears_in"
                                                       :type {:kind "LIST"
                                                              :name nil
                                                              :ofType {:enumValues [{:name "NEWHOPE"}
                                                                                    {:name "EMPIRE"}
                                                                                    {:name "JEDI"}]}}}
                                                      {:name "arch_enemy"
                                                       :type {:kind "NON_NULL"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "bar"
                                                       :type {:kind "INTERFACE"
                                                              :name "character"
                                                              :ofType nil}}
                                                      {:name "best_friend"
                                                       :type {:kind "INTERFACE"
                                                              :name "character"
                                                              :ofType nil}}
                                                      {:name "droids"
                                                       :type {:kind "NON_NULL"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "enemies"
                                                       :type {:kind "LIST"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "family"
                                                       :type {:kind "NON_NULL"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "foo"
                                                       :type {:kind "NON_NULL"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "forceSide"
                                                       :type {:kind "OBJECT"
                                                              :name "force"
                                                              :ofType nil}}
                                                      {:name "friends"
                                                       :type {:kind "LIST"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "id"
                                                       :type {:kind "SCALAR"
                                                              :name "String"
                                                              :ofType nil}}
                                                      {:name "name"
                                                       :type {:kind "SCALAR"
                                                              :name "String"
                                                              :ofType nil}}]
                                             :kind "INTERFACE"
                                             :name "character"}}
                                     {:name "droids"
                                      :type {:fields []
                                             :kind "NON_NULL"
                                             :name nil}}
                                     {:name "enemies"
                                      :type {:fields []
                                             :kind "LIST"
                                             :name nil}}
                                     {:name "family"
                                      :type {:fields []
                                             :kind "NON_NULL"
                                             :name nil}}
                                     {:name "foo"
                                      :type {:fields []
                                             :kind "NON_NULL"
                                             :name nil}}
                                     {:name "forceSide"
                                      :type {:fields [{:name "id"
                                                       :type {:kind "SCALAR"
                                                              :name "String"
                                                              :ofType nil}}
                                                      {:name "members"
                                                       :type {:kind "LIST"
                                                              :name nil
                                                              :ofType {:enumValues []}}}
                                                      {:name "name"
                                                       :type {:kind "SCALAR"
                                                              :name "String"
                                                              :ofType nil}}]
                                             :kind "OBJECT"
                                             :name "force"}}
                                     {:name "friends"
                                      :type {:fields []
                                             :kind "LIST"
                                             :name nil}}
                                     {:name "id"
                                      :type {:fields []
                                             :kind "SCALAR"
                                             :name "String"}}
                                     {:name "name"
                                      :type {:fields []
                                             :kind "SCALAR"
                                             :name "String"}}]
                            :kind "INTERFACE"
                            :name "character"}}}
           (execute q)))))

(deftest schema-introspection-query
  (let [q "{ __schema { types { name }}}"]
    ;; Note that schema types are explicitly absent.
    (is (= {:data {:__schema {:types [{:name "Boolean"}
                                      {:name "Date"}
                                      {:name "Float"}
                                      {:name "ID"}
                                      {:name "Int"}
                                      {:name "MutationRoot"}
                                      {:name "QueryRoot"}
                                      {:name "String"}
                                      {:name "character"}
                                      {:name "droid"}
                                      {:name "echoArgs"}
                                      {:name "episode"}
                                      {:name "force"}
                                      {:name "galaxy-date"}
                                      {:name "human"}
                                      {:name "nestedInputObject"}
                                      {:name "testInputObject"}]}}}
           (execute q))))
  (let [q "{ __schema
             { types { name kind description }
               queryType { name kind fields { name }}
             }
           }"]
    ;; TODO: Should QueryRoot and MutationRoot appear?
    (is (= {:data {:__schema {:queryType {:fields [{:name "droid"}
                                                   {:name "echoArgs"}
                                                   {:name "hero"}
                                                   {:name "human"}
                                                   {:name "now"}]
                                          :kind "OBJECT"
                                          :name "QueryRoot"}
                              :types [{:description nil
                                       :kind "SCALAR"
                                       :name "Boolean"}
                                      {:description nil
                                       :kind "SCALAR"
                                       :name "Date"}
                                      {:description nil
                                       :kind "SCALAR"
                                       :name "Float"}
                                      {:description nil
                                       :kind "SCALAR"
                                       :name "ID"}
                                      {:description nil
                                       :kind "SCALAR"
                                       :name "Int"}
                                      {:description "Root of all mutations."
                                       :kind "OBJECT"
                                       :name "MutationRoot"}
                                      {:description "Root of all queries."
                                       :kind "OBJECT"
                                       :name "QueryRoot"}
                                      {:description nil
                                       :kind "SCALAR"
                                       :name "String"}
                                      {:description nil
                                       :kind "INTERFACE"
                                       :name "character"}
                                      {:description nil
                                       :kind "OBJECT"
                                       :name "droid"}
                                      {:description nil
                                       :kind "OBJECT"
                                       :name "echoArgs"}
                                      {:description "The episodes of the original Star Wars trilogy."
                                       :kind "ENUM"
                                       :name "episode"}
                                      {:description nil
                                       :kind "OBJECT"
                                       :name "force"}
                                      {:description nil
                                       :kind "OBJECT"
                                       :name "galaxy-date"}
                                      {:description nil
                                       :kind "OBJECT"
                                       :name "human"}
                                      {:description nil
                                       :kind "INPUT_OBJECT"
                                       :name "nestedInputObject"}
                                      {:description nil
                                       :kind "INPUT_OBJECT"
                                       :name "testInputObject"}]}}}
           (execute q)))))

(deftest graphiql-introspection-query
  (let [q "query IntrospectionQuery {
            __schema {
              queryType { name }
              mutationType { name }
              types {
                ...FullType
              }
              directives {
                name
                description
                args {
                  ...InputValue
                }
              }
            }
          }
          fragment FullType on __Type {
            kind
            name
            description
            fields(includeDeprecated: true) {
              name
              description
              args {
                ...InputValue
              }
              type {
                ...TypeRef
              }
              isDeprecated
              deprecationReason
            }
            inputFields {
              ...InputValue
            }
            interfaces {
              ...TypeRef
            }
            enumValues(includeDeprecated: true) {
              name
              description
              isDeprecated
              deprecationReason
            }
            possibleTypes {
              ...TypeRef
            }
          }
          fragment InputValue on __InputValue {
            name
            description
            type { ...TypeRef }
            defaultValue
          }
          fragment TypeRef on __Type {
            kind
            name
            ofType {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                }
              }
            }
          }"]
    ;; This giant test is difficult to maintain, and very subject to breakage if anyone
    ;; adds anything to the test-schema; perhaps we can change it to just ensure that
    ;; there are no errors, and break it up into smaller tests against small, ad-hoc
    ;; features?
    (is (= {:directives []
            :mutationType {:name "MutationRoot"}
            :queryType {:name "QueryRoot"}
            :types [{:description nil
                     :enumValues []
                     :fields []
                     :inputFields []
                     :interfaces []
                     :kind "SCALAR"
                     :name "Boolean"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields []
                     :inputFields []
                     :interfaces []
                     :kind "SCALAR"
                     :name "Date"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields []
                     :inputFields []
                     :interfaces []
                     :kind "SCALAR"
                     :name "Float"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields []
                     :inputFields []
                     :interfaces []
                     :kind "SCALAR"
                     :name "ID"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields []
                     :inputFields []
                     :interfaces []
                     :kind "SCALAR"
                     :name "Int"
                     :possibleTypes []}
                    {:description "Root of all mutations."
                     :enumValues []
                     :fields [{:args [{:defaultValue nil
                                       :description nil
                                       :name "does_nothing"
                                       :type {:kind "SCALAR"
                                              :name "String"
                                              :ofType nil}}
                                      {:defaultValue nil
                                       :description nil
                                       :name "episodes"
                                       :type {:kind "NON_NULL"
                                              :name nil
                                              :ofType {:kind "LIST"
                                                       :name nil
                                                       :ofType {:kind "ENUM"
                                                                :name "episode"
                                                                :ofType nil}}}}
                                      {:defaultValue nil
                                       :description nil
                                       :name "id"
                                       :type {:kind "NON_NULL"
                                              :name nil
                                              :ofType {:kind "SCALAR"
                                                       :name "String"
                                                       :ofType nil}}}]
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "addHeroEpisodes"
                               :type {:kind "INTERFACE"
                                      :name "character"
                                      :ofType nil}}
                              {:args [{:defaultValue nil
                                       :description nil
                                       :name "id"
                                       :type {:kind "NON_NULL"
                                              :name nil
                                              :ofType {:kind "SCALAR"
                                                       :name "String"
                                                       :ofType nil}}}
                                      {:defaultValue nil
                                       :description nil
                                       :name "newHomePlanet"
                                       :type {:kind "SCALAR"
                                              :name "String"
                                              :ofType nil}}]
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "changeHeroHomePlanet"
                               :type {:kind "OBJECT"
                                      :name "human"
                                      :ofType nil}}
                              {:args [{:defaultValue nil
                                       :description nil
                                       :name "from"
                                       :type {:kind "SCALAR"
                                              :name "String"
                                              :ofType nil}}
                                      {:defaultValue "Rey"
                                       :description nil
                                       :name "to"
                                       :type {:kind "SCALAR"
                                              :name "String"
                                              :ofType nil}}]
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "changeHeroName"
                               :type {:kind "INTERFACE"
                                      :name "character"
                                      :ofType nil}}]
                     :inputFields []
                     :interfaces []
                     :kind "OBJECT"
                     :name "MutationRoot"
                     :possibleTypes []}
                    {:description "Root of all queries."
                     :enumValues []
                     :fields [{:args [{:defaultValue "2001"
                                       :description nil
                                       :name "id"
                                       :type {:kind "SCALAR"
                                              :name "String"
                                              :ofType nil}}]
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "droid"
                               :type {:kind "OBJECT"
                                      :name "droid"
                                      :ofType nil}}
                              {:args [{:defaultValue nil
                                       :description nil
                                       :name "inputObject"
                                       :type {:kind "INPUT_OBJECT"
                                              :name "testInputObject"
                                              :ofType nil}}
                                      {:defaultValue nil
                                       :description nil
                                       :name "integer"
                                       :type {:kind "SCALAR"
                                              :name "Int"
                                              :ofType nil}}
                                      {:defaultValue nil
                                       :description nil
                                       :name "integerArray"
                                       :type {:kind "LIST"
                                              :name nil
                                              :ofType {:kind "SCALAR"
                                                       :name "Int"
                                                       :ofType nil}}}]
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "echoArgs"
                               :type {:kind "OBJECT"
                                      :name "echoArgs"
                                      :ofType nil}}
                              {:args [{:defaultValue nil
                                       :description nil
                                       :name "episode"
                                       :type {:kind "ENUM"
                                              :name "episode"
                                              :ofType nil}}]
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "hero"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "INTERFACE"
                                               :name "character"
                                               :ofType nil}}}
                              {:args [{:defaultValue "1001"
                                       :description nil
                                       :name "id"
                                       :type {:kind "SCALAR"
                                              :name "String"
                                              :ofType nil}}]
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "human"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "OBJECT"
                                               :name "human"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "now"
                               :type {:kind "OBJECT"
                                      :name "galaxy-date"
                                      :ofType nil}}]
                     :inputFields []
                     :interfaces []
                     :kind "OBJECT"
                     :name "QueryRoot"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields []
                     :inputFields []
                     :interfaces []
                     :kind "SCALAR"
                     :name "String"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields [{:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "appears_in"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "ENUM"
                                               :name "episode"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "arch_enemy"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "INTERFACE"
                                               :name "character"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "bar"
                               :type {:kind "INTERFACE"
                                      :name "character"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "best_friend"
                               :type {:kind "INTERFACE"
                                      :name "character"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "droids"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "LIST"
                                               :name nil
                                               :ofType {:kind "NON_NULL"
                                                        :name nil
                                                        :ofType {:kind "INTERFACE"
                                                                 :name "character"}}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "enemies"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "NON_NULL"
                                               :name nil
                                               :ofType {:kind "INTERFACE"
                                                        :name "character"
                                                        :ofType nil}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "family"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "LIST"
                                               :name nil
                                               :ofType {:kind "INTERFACE"
                                                        :name "character"
                                                        :ofType nil}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "foo"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "SCALAR"
                                               :name "String"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "forceSide"
                               :type {:kind "OBJECT"
                                      :name "force"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "friends"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "INTERFACE"
                                               :name "character"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "id"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "name"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}]
                     :inputFields []
                     :interfaces []
                     :kind "INTERFACE"
                     :name "character"
                     :possibleTypes [{:kind "OBJECT"
                                      :name "droid"
                                      :ofType nil}
                                     {:kind "OBJECT"
                                      :name "human"
                                      :ofType nil}]}
                    {:description nil
                     :enumValues []
                     :fields [{:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "accessories"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "SCALAR"
                                               :name "String"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "appears_in"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "ENUM"
                                               :name "episode"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "arch_enemy"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "INTERFACE"
                                               :name "character"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "bar"
                               :type {:kind "INTERFACE"
                                      :name "character"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "best_friend"
                               :type {:kind "INTERFACE"
                                      :name "character"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "droids"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "LIST"
                                               :name nil
                                               :ofType {:kind "NON_NULL"
                                                        :name nil
                                                        :ofType {:kind "INTERFACE"
                                                                 :name "character"}}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "enemies"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "NON_NULL"
                                               :name nil
                                               :ofType {:kind "INTERFACE"
                                                        :name "character"
                                                        :ofType nil}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "family"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "LIST"
                                               :name nil
                                               :ofType {:kind "INTERFACE"
                                                        :name "character"
                                                        :ofType nil}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "foo"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "SCALAR"
                                               :name "String"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "forceSide"
                               :type {:kind "OBJECT"
                                      :name "force"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "friends"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "INTERFACE"
                                               :name "character"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "id"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "incept_date"
                               :type {:kind "SCALAR"
                                      :name "Int"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "name"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "primary_function"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "SCALAR"
                                               :name "String"
                                               :ofType nil}}}]
                     :inputFields []
                     :interfaces [{:kind "INTERFACE"
                                   :name "character"
                                   :ofType nil}]
                     :kind "OBJECT"
                     :name "droid"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields [{:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "inputObject"
                               :type {:kind "INPUT_OBJECT"
                                      :name "testInputObject"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "integer"
                               :type {:kind "SCALAR"
                                      :name "Int"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "integerArray"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "SCALAR"
                                               :name "Int"
                                               :ofType nil}}}]
                     :inputFields []
                     :interfaces []
                     :kind "OBJECT"
                     :name "echoArgs"
                     :possibleTypes []}
                    {:description "The episodes of the original Star Wars trilogy."
                     :enumValues [{:deprecationReason nil
                                   :description nil
                                   :isDeprecated false
                                   :name "NEWHOPE"}
                                  {:deprecationReason nil
                                   :description nil
                                   :isDeprecated false
                                   :name "EMPIRE"}
                                  {:deprecationReason nil
                                   :description nil
                                   :isDeprecated false
                                   :name "JEDI"}]
                     :fields []
                     :inputFields []
                     :interfaces []
                     :kind "ENUM"
                     :name "episode"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields [{:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "id"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "members"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "INTERFACE"
                                               :name "character"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "name"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}]
                     :inputFields []
                     :interfaces []
                     :kind "OBJECT"
                     :name "force"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields [{:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "date"
                               :type {:kind "SCALAR"
                                      :name "Date"
                                      :ofType nil}}]
                     :inputFields []
                     :interfaces []
                     :kind "OBJECT"
                     :name "galaxy-date"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields [{:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "appears_in"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "ENUM"
                                               :name "episode"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "arch_enemy"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "INTERFACE"
                                               :name "character"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "bar"
                               :type {:kind "INTERFACE"
                                      :name "character"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "best_friend"
                               :type {:kind "INTERFACE"
                                      :name "character"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "droids"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "LIST"
                                               :name nil
                                               :ofType {:kind "NON_NULL"
                                                        :name nil
                                                        :ofType {:kind "INTERFACE"
                                                                 :name "character"}}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "enemies"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "NON_NULL"
                                               :name nil
                                               :ofType {:kind "INTERFACE"
                                                        :name "character"
                                                        :ofType nil}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "error_field"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "family"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "LIST"
                                               :name nil
                                               :ofType {:kind "INTERFACE"
                                                        :name "character"
                                                        :ofType nil}}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "foo"
                               :type {:kind "NON_NULL"
                                      :name nil
                                      :ofType {:kind "SCALAR"
                                               :name "String"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "forceSide"
                               :type {:kind "OBJECT"
                                      :name "force"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "friends"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "INTERFACE"
                                               :name "character"
                                               :ofType nil}}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "homePlanet"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "id"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "multiple_errors_field"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "name"
                               :type {:kind "SCALAR"
                                      :name "String"
                                      :ofType nil}}
                              {:args []
                               :deprecationReason nil
                               :description nil
                               :isDeprecated false
                               :name "primary_function"
                               :type {:kind "LIST"
                                      :name nil
                                      :ofType {:kind "SCALAR"
                                               :name "String"
                                               :ofType nil}}}]
                     :inputFields []
                     :interfaces [{:kind "INTERFACE"
                                   :name "character"
                                   :ofType nil}]
                     :kind "OBJECT"
                     :name "human"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields []
                     :inputFields [{:defaultValue nil
                                    :description nil
                                    :name "date"
                                    :type {:kind "SCALAR"
                                           :name "Date"
                                           :ofType nil}}
                                   {:defaultValue nil
                                    :description nil
                                    :name "integerArray"
                                    :type {:kind "LIST"
                                           :name nil
                                           :ofType {:kind "SCALAR"
                                                    :name "Int"
                                                    :ofType nil}}}]
                     :interfaces []
                     :kind "INPUT_OBJECT"
                     :name "nestedInputObject"
                     :possibleTypes []}
                    {:description nil
                     :enumValues []
                     :fields []
                     :inputFields [{:defaultValue nil
                                    :description nil
                                    :name "integer"
                                    :type {:kind "SCALAR"
                                           :name "Int"
                                           :ofType nil}}
                                   {:defaultValue nil
                                    :description nil
                                    :name "nestedInputObject"
                                    :type {:kind "INPUT_OBJECT"
                                           :name "nestedInputObject"
                                           :ofType nil}}
                                   {:defaultValue nil
                                    :description nil
                                    :name "string"
                                    :type {:kind "SCALAR"
                                           :name "String"
                                           :ofType nil}}]
                     :interfaces []
                     :kind "INPUT_OBJECT"
                     :name "testInputObject"
                     :possibleTypes []}]}
           (-> (execute q) :data :__schema)))))

(deftest mixed-use-of-keywords
  (let [schema (schema/compile {:queries {:search {:type '(non-null :ID)
                                                   :args {:term {:type '(non-null String)}}}}})
        q "
        query { __schema { queryType {
          name
          fields {
            name
            args { ...InputValue }
            type { ...TypeRef }
          }
         }}}

          fragment InputValue on __InputValue {
            name
            type { ...TypeRef }
          }

          fragment TypeRef on __Type {
            name
            kind
            ofType { name kind }
          }"]
    (is (= {:data {:__schema {:queryType {:fields [{:args [{:name "term"
                                                            :type {:kind "NON_NULL"
                                                                   :name nil
                                                                   :ofType {:kind "SCALAR"
                                                                            :name "String"}}}]
                                                    :name "search"
                                                    :type {:kind "NON_NULL"
                                                           :name nil
                                                           :ofType {:kind "SCALAR"
                                                                    :name "ID"}}}]
                                          :name "QueryRoot"}}}}
           (simplify (lacinia/execute schema q nil nil))))))
