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
    [com.walmartlabs.lacinia.parser.schema :refer [parse-schema]]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.test-utils :refer [execute]]
    [com.walmartlabs.test-reporting :refer [reporting]]
    [com.walmartlabs.lacinia.schema :as schema]))

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

(deftest essentials
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (-> sdl
                   (parse-schema {:federation {:entity-resolvers {:User (fn [_ _ _] nil)}}})
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

(deftest entity-resolvers
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers
                                                 {:User resolve-user-external
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

(deftest missing-entity-resolvers
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers
                                                 {:User resolve-user-external}}}))
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
