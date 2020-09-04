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
    [com.walmartlabs.test-utils :refer [execute]]
    [com.walmartlabs.test-reporting :refer [reporting]]
    [com.walmartlabs.lacinia.schema :as schema]))

(deftest essentials
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers {:User (fn [_ _ _] nil)}}}))]

    (is (= {:data {:_service {:sdl sdl}}}
           (execute schema
                    "{ _service { sdl }}")))

    (is (= {:data {:entities {:members [{:name "Product"}
                                        {:name "User"}]
                              :name "_Entity"}}}
           (execute schema
                    "{ entities: __type(name: \"_Entity\") { name members: possibleTypes { name }}}")))))

(defn ^:private resolve-user
  [_ _ reps]
  (for [{:keys [id]} reps]
    (schema/tag-with-type
      {:id id
       :name (str "User #" id)}
      :User)))

(def entities-query
  "
query($reps : [_Any!]!) {
  entities: _entities(representations: $reps) {
    __typename

    ... on User { id name }
    }
  }
}")

(deftest entity-resolvers
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (schema/compile
                 (parse-schema sdl {:federation {:entity-resolvers
                                                 {:User resolve-user}}}))]

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
                    nil)))))

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
                         (into #{}))
        union-names (->> result
                         :data :schema :types
                         (filter #(-> % :kind (= :UNION)))
                         (map :name)
                         (into #{}))]
    (reporting result
               (is (contains? field-names "_service"))
               (is (not (contains? field-names "_entities")))
               (is (= #{"Stuff"} union-names)))))
