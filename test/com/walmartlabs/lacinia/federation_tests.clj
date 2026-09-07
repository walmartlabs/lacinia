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
    [clojure.walk :as walk]
    [clojure.string :refer [trim]]
    [com.walmartlabs.lacinia.parser.schema :refer [parse-schema]]
    [com.walmartlabs.lacinia.resolve :refer [FieldResolver resolve-as]]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.test-utils :refer [execute]]
    [com.walmartlabs.test-reporting :refer [reporting]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.federation :refer [inject-federation generate-sdl foundation-types]]))

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
  (let [sample-edn-1 '{:roots {:query :MyQuery
                                :mutation :Mutation}
                        :interfaces
                        {:Node
                         {:fields
                          {:id
                           {:type (non-null ID)}}}}
                        :objects
                        {:MyQuery
                         {:fields
                          {:todo
                           {:type :Todo
                            :description "\"\"\"Get one todo item\""
                            :args
                            {:id
                             {:type (non-null ID)
                              :default-value "\"default-node-id"}}}
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
                         {:locations #{:field-definition}}}}
        sample-edn-2 '{:queries
                       {:node
                        {:description "node query"
                         :type Node
                         :args {:id {:type (non-null ID)}}}}
                       :roots
                       {:query :CustomQuery}}
        sample-sdl-2 "schema {\n  query: CustomQuery\n}\n\ntype CustomQuery{\n  \"node query\"\n  node(id: ID!): Node\n}"]
    
    (is (= (-> sample-edn-1 generate-sdl parse-schema) sample-edn-1))
    (is (= (generate-sdl sample-edn-2) sample-sdl-2))))

(deftest only-edn-schema-essential
  (let [edn (-> "dev-resources/edn-federation.edn" slurp read-string)
        sdl (-> "dev-resources/edn-federation.sdl" slurp trim)
        schema (-> (merge-with merge foundation-types edn)
                   (inject-federation {:User always-nil
                                       :Account always-nil
                                       :Product always-nil})
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

(deftest sdl-is-independent-of-map-insertion-order
  (let [fields (into {} (for [i (range 12)] [(keyword (str "field" i)) {:type 'String}]))
        schema {:roots {:query :Root}
                :queries fields
                :objects {:Root {:fields {:lookup {:type 'String
                                                    :args {:z {:type 'String} :a {:type 'String}}}}}
                          :Zebra {:fields fields}
                          :Alpha {:fields fields}}
                :directive-defs {:zed {:locations #{:object :interface}}
                                 :alpha {:locations #{:object :interface}}}
                :interfaces {:Zulu {:fields fields} :Able {:fields fields}}
                :input-objects {:ZuluInput {:fields fields} :AbleInput {:fields fields}}
                :scalars {:ZuluScalar {} :AbleScalar {}}
                :enums {:ZuluEnum {:values [:Z :A]} :AbleEnum {:values [:B :A]}}
                :unions {:ZuluUnion {:members [:Zebra :Alpha]} :AbleUnion {:members [:Alpha :Zebra]}}}
        reorder (fn [comparator]
                  (walk/postwalk #(if (map? %) (into (sorted-map-by comparator) %) %) schema))
        sdl (generate-sdl schema)]
    (is (= sdl (generate-sdl (reorder compare)) (generate-sdl (reorder #(compare %2 %1)))))
    (is (.contains sdl "lookup(a: String, z: String): String"))
    (is (.contains sdl "ZuluEnum{\n  Z\n  A\n}"))
    (is (= (parse-schema sdl) (parse-schema (generate-sdl (reorder compare)))))))

(deftest descriptions-and-string-values-round-trip
  (let [text "quotes \"\"\" and \\ slash\nline\rreturn\ttab\bbackspace\fformfeed"
        input {:queries {:echo {:type 'String
                                :description text
                                :args {:value {:type 'String :description text :default-value text}}}}}
        parsed (parse-schema (generate-sdl input))]
    (is (= text (get-in parsed [:objects :Query :fields :echo :description])))
    (is (= text (get-in parsed [:objects :Query :fields :echo :args :value :description])))
    (is (= text (get-in parsed [:objects :Query :fields :echo :args :value :default-value])))))

(deftest custom-roots-fold-all-operation-shorthands
  (let [input '{:roots {:query :Read :mutation :Write :subscription :Watch}
                :objects {:Read {:fields {:existing {:type String}}}}
                :queries {:read {:type String}}
                :mutations {:write {:type String}}
                :subscriptions {:watch {:type String}}}
        parsed (parse-schema (generate-sdl input))]
    (is (= (:roots input) (:roots parsed)))
    (is (= #{:existing :read} (-> parsed :objects :Read :fields keys set)))
    (is (= #{:write} (-> parsed :objects :Write :fields keys set)))
    (is (= #{:watch} (-> parsed :objects :Watch :fields keys set)))))

(deftest generated-service-sdl-filters-only-foundation-definitions
  (let [input (merge-with merge foundation-types
                         '{:objects {:Query {:fields {:value {:type String}}}}
                           :scalars {:Custom {}}
                           :directive-defs {:custom {:locations #{:field-definition}}}})
        generated (inject-federation input {})
        resolver (get-in generated [:objects :Query :fields :_service :resolve])
        sdl (:sdl (resolver nil nil nil))]
    (is (.contains (generate-sdl input) "scalar _Any"))
    (is (.contains sdl "scalar Custom"))
    (is (.contains sdl "directive @custom"))
    (is (not (re-find #"_Any|_FieldSet|_Service|_entities|_service|directive @external" sdl)))
    (is (contains? (:scalars generated) :_Any))
    (is (= sdl (-> sdl parse-schema generate-sdl)))))
