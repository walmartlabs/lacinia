; Copyright (c) 2020-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.federation-tests
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.string :refer [trim]]
    [com.walmartlabs.lacinia.parser.schema :refer [parse-schema]]
    [com.walmartlabs.lacinia.resolve :refer [FieldResolver resolve-as]]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.test-utils :refer [execute]]
    [com.walmartlabs.test-reporting :refer [reporting]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.federation :refer [inject-federation generate-sdl]]))

(defn ^:private resolve-user
  [_ {:keys [id]} _]
  {:id id
   :name (str "User #" id)})

(defn ^:private resolve-user-external
  [_ _ reps]
  (for [{:keys [id]} reps]
    (schema/tag-with-type
      {:id id
       :name (str "User #" id)}
      :User)))

(defn ^:private resolve-account
  [_ _ reps]
  (for [{:keys [acct_number]} reps]
    (schema/tag-with-type
      {:acct_number acct_number
       :name (str "Account #" acct_number)}
      :Account)))

(def entities-query
  "
query($reps : [_Any!]!) {
  entities: _entities(representations: $reps) {
    __typename

    ... on User { id name }

    ... on Account { acct_number name }
    }
  }
}")

(defn always-nil
  [_ _ _]
  nil)

(deftest essentials
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (-> sdl
                   (parse-schema {:federation {:entity-resolvers {:User always-nil
                                                                  :Account always-nil
                                                                  :Product always-nil}}})
                   (util/inject-resolvers {:Query/user_by_id resolve-user})
                   schema/compile)]

    (is (= {:data {:_service {:sdl sdl}}}
           (execute schema
                    "{ _service { sdl }}")))

    (is (= {:data {:entities {:members [{:name "Account"}
                                        {:name "Product"}
                                        {:name "User"}]
                              :name "_Entity"}}}
           (execute schema
                    "{ entities: __type(name: \"_Entity\") { name members: possibleTypes { name }}}")))

    (is (= {:data {:user_by_id {:id 9998
                                :name "User #9998"}}}
           (execute schema
                    "{ user_by_id(id: 9998) { id name }}")))))

(deftest missing-entity-resolver
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        ex (is (thrown? Exception
                        (-> sdl
                            (parse-schema {:federation {:entity-resolvers {:User always-nil
                                                                           :Product always-nil}}}))))]
    (when ex
      (is (= "Must provide entity resolvers for each entity (each type with @key)" (ex-message ex)))
      (is (= {:actual [:Product
                       :User]
              :expected [:Account
                         :Product
                         :User]}
             (ex-data ex))))))

(deftest entity-resolvers
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers
                                                 {:User resolve-user-external
                                                  :Product always-nil
                                                  :Account resolve-account}}}))]

    (is (= {:data {:entities []}}
           (execute schema
                    entities-query
                    {:reps []}
                    nil)))

    (is (= {:data {:entities [{:__typename :User
                               :id 1001
                               :name "User #1001"}
                              {:__typename :User
                               :id 2002
                               :name "User #2002"}]}}
           (execute schema
                    entities-query
                    {:reps [{:__typename "User"
                             :id 1001}
                            {:__typename "User"
                             :id 2002}]}
                    nil)))

    (is (= {:data {:entities [{:__typename :User
                               :id 1001
                               :name "User #1001"}
                              {:__typename :Account
                               :acct_number "2002"
                               :name "Account #2002"}]}}
           (execute schema
                    entities-query
                    {:reps [{:__typename "User"
                             :id 1001}
                            {:__typename "Account"
                             :acct_number 2002}]}
                    nil)))))

(deftest entity-resolver-as-field-resolver-instance
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        user-fr (reify FieldResolver

                  (resolve-value [_ _ _ reps]
                    (for [{:keys [id]} reps]
                      (schema/tag-with-type
                        {:id id
                         :name (str "FR-User #" id)}
                        :User))))
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers
                                                 {:User user-fr
                                                  :Product always-nil
                                                  :Account resolve-account}}}))]
    (is (= {:data {:entities [{:__typename :User
                               :id 1001
                               :name "FR-User #1001"}
                              {:__typename :User
                               :id 2002
                               :name "FR-User #2002"}]}}
           (execute schema
                    entities-query
                    {:reps [{:__typename "User"
                             :id 1001}
                            {:__typename "User"
                             :id 2002}]}
                    nil)))))

(deftest entity-resolver-returns-resolver-result
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        user-entity-resolver (fn [_ _ reps]
                               (resolve-as
                                 (resolve-user-external nil nil reps)
                                 {:message "Error in user entity resolver"}))
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers
                                                 {:User user-entity-resolver
                                                  :Product always-nil
                                                  :Account resolve-account}}}))]
    (is (= {:data {:entities [{:__typename :User
                               :id 1001
                               :name "User #1001"}
                              {:__typename :User
                               :id 2002
                               :name "User #2002"}]}
            :errors '[{:extensions {:arguments {:representations $reps}}
                       :locations [{:column 3
                                    :line 3}]
                       :message "Error in user entity resolver"
                       :path [:entities]}]}
           (execute schema
                    entities-query
                    {:reps [{:__typename "User"
                             :id 1001}
                            {:__typename "User"
                             :id 2002}]}
                    nil)))))

(deftest missing-entity-resolvers
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers
                                                 {:User resolve-user-external
                                                  :Product always-nil
                                                  :Account always-nil}}}))
        query (fn [& reps] (execute schema entities-query {:reps reps} nil))]

    (is (= '{:data {:entities []}
             :errors [{:extensions {:arguments {:representations $reps}}
                       :locations [{:column 3
                                    :line 3}]
                       :message "No entity resolver for type `DoesNotExist'"
                       :path [:entities]}]}
           (query {:__typename "DoesNotExist"
                   :id 9999})))

    (is (= '{:data {:entities [{:__typename :User
                                :id 3003
                                :name "User #3003"}
                               {:__typename :User
                                :id 4004
                                :name "User #4004"}]}
             :errors [{:extensions {:arguments {:representations $reps}}
                       :locations [{:column 3
                                    :line 3}]
                       :message "No entity resolver for type `DoesNotExist'"
                       :path [:entities]}]}
           (query {:__typename "User"
                   :id 3003}
                  {:__typename "DoesNotExist"
                   :id 9998}
                  {:__typename "User"
                   :id 4004})))))

(deftest no-entities
  (let [sdl (slurp "dev-resources/no-entities-federation.sdl")
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers {}}}))
        result (->> (execute schema
                             "
                             {
                               schema: __schema {
                                 query: queryType {
                                   fields { name }
                                 }
                                 types { kind name }
                               }
                             }"
                             ))
        field-names (->> result
                         :data :schema :query :fields
                         (map :name)
                         set)
        union-names (->> result
                         :data :schema :types
                         (filter #(-> % :kind (= :UNION)))
                         (map :name)
                         set)]
    (reporting result
      (is (contains? field-names "_service"))
      (is (not (contains? field-names "_entities")))
      (is (= #{"Stuff"} union-names)))))

(deftest edn-schema->sdl-schema
  (let [sample-schema '{:roots {:query :query
                                :mutation :mutation}
                        :interfaces
                        {:Node
                         {:fields
                          {:id
                           {:type (non-null ID)}}}}
                        :objects
                        {:Query
                         {:fields
                          {:todo
                           {:type :Todo
                            :description "\"Get one todo item\""
                            :args
                            {:id
                             {:type (non-null ID)}}}
                           :allTodos
                           {:type (non-null (list (non-null :Todo))) :description "List of all todo items"}}}
                         :Mutation
                         {:fields
                          {:addTodo
                           {:type (non-null :Todo)
                            :args
                            {:name
                             {:type (non-null String) :description "Name for the todo item"}
                             :priority
                             {:type :Priority :description "Priority level of todo item" :default-value :LOW}}}
                           :removeTodo
                           {:type (non-null :Todo)
                            :args
                            {:id
                             {:type (non-null ID)}}}}}
                         :Todo
                         {:implements  [:Node]
                          :fields
                          {:id
                           {:type (non-null ID)}
                           :name
                           {:type (non-null String)}
                           :description
                           {:type String :description "Useful description for todo item"}
                           :priority
                           {:type (non-null :Priority)}}}}
                        :enums
                        {:Priority
                         {:values [{:enum-value :LOW}
                                   {:enum-value :MEDIUM}
                                   {:enum-value :HIGH}]}}
                        :unions
                        {:_Entity
                         {:members [:Todo]}}
                        :scalars
                        {:FieldSet
                         {}}
                        :directive-defs
                        {:key
                         {:locations #{:interface :object}
                          :args
                          {:fields
                           {:type (non-null :FieldSet)}
                           :resolvable
                           {:type Boolean :default-value true}}}
                         :external
                         {:locations #{:field-definition}}}}]
    (is (= (-> sample-schema generate-sdl parse-schema) sample-schema))))

(deftest only-edn-schama-essential
  (let [edn (-> "dev-resources/edn-federation.edn" slurp read-string)
        sdl (-> "dev-resources/edn-federation.sdl" slurp trim)
        schema (-> edn
                   (inject-federation {:User always-nil
                                       :Account always-nil
                                       :Product always-nil})
                   (util/inject-resolvers {:Query/user_by_id resolve-user})
                   (util/attach-scalar-transformers {:_Any/parser identity
                                                     :_Any/serializer identity
                                                     :_FieldSet/parser identity
                                                     :_FieldSet/serializer identity
                                                     :link__Import/parser identity
                                                     :link__Import/serializer identity})
                   schema/compile)]
    (is (= {:data {:_service {:sdl sdl}}}
           (execute schema
                    "{ _service { sdl }}")))

    (is (= {:data {:entities {:members [{:name "Account"}
                                        {:name "Product"}
                                        {:name "User"}]
                              :name "_Entity"}}}
           (execute schema
                    "{ entities: __type(name: \"_Entity\") { name members: possibleTypes { name }}}")))

    (is (= {:data {:user_by_id {:id 9998
                                :name "User #9998"}}}
           (execute schema
                    "{ user_by_id(id: 9998) { id name }}")))))
