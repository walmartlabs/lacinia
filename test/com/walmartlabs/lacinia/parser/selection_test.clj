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

(ns com.walmartlabs.lacinia.parser.selection-test
  "Tests related to the executor/selection function and related data, introduced in 0.38.0."
  (:require
    [clojure.test :refer [is deftest use-fixtures]]
    [com.walmartlabs.lacinia.executor :as executor]
    [com.walmartlabs.lacinia.selection :as selection]
    [com.walmartlabs.test-utils :refer [compile-sdl-schema execute]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.parser.schema :refer [parse-schema]]
    [clojure.java.io :as io]
    [com.walmartlabs.lacinia.util :as util])
  (:import
    (java.util UUID)))

(def ^:private *facts
  "A collection of key/value facts gathered during execution of a test."
  (atom []))

(defn ^:private note
  [& kvs]
  (let [pairs (map vec (partition-all 2 kvs))]
    (swap! *facts into pairs)))

(defn ^:private reset-facts
  [f]
  (reset! *facts [])
  (f))

(use-fixtures :each reset-facts)

(defn ^:private root-type
  [context]
  (-> context
      executor/selection
      selection/root-value-type))

(defn ^:private auth-role
  [context]
  (-> context
      root-type
      selection/directives
      :auth
      first
      selection/arguments
      :role))

(deftest access-to-selection
  (let [f (fn [context _ _]
            (let [s (executor/selection context)
                  directives (selection/directives s)]
              (note :selection {:kind (selection/selection-kind s)
                               :qualified-name (selection/qualified-name s)
                               :field-name (selection/field-name s)
                               :alias (selection/alias-name s)}
                    :directive-keys (-> directives keys sort)
                    :directive-names (->> directives
                                          :concise
                                          (map selection/directive-type))))
            "Done")
        schema (compile-sdl-schema "selection/simple.sdl"
                                   {:Query/basic f})
        result (execute schema "{ basic @concise }")]
    (is (= {:data {:basic "Done"}}
           result))
    (is (= [[:selection {:kind :field
                          :qualified-name :Query/basic
                          :field-name :basic
                          :alias :basic}]
             [:directive-keys [:concise]]
             [:directive-names [:concise]]]
           @*facts))))

(deftest access-to-type-system-directives
  (let [f (fn [context _ _]
            (let [s (executor/selection context)
                  field (selection/field s)
                  directives (selection/directives field)]
              (note :field {:root-type-name (selection/root-type-name field)
                            :qualified-name (selection/qualified-name field)}
                    :directives  (->> directives
                                      :auth
                                      (mapv (juxt selection/directive-type selection/arguments )))))
            "Fini")
        schema (compile-sdl-schema "selection/simple.sdl"
                                   {:Query/basic f})
        result (execute schema "{ basic  }")]
    (is (= {:data {:basic "Fini"}}
           result))
    (is (= [[:field
             {:qualified-name :Query/basic
              :root-type-name :String}]
            [:directives
             [[:auth
               {:role "basic"}]]]]
           @*facts))))

(deftest access-to-type
  (let [me (fn [context _ _]
             (let [t (root-type context)]
               (note :type {:type-name (selection/type-name t)
                           :type-kind (selection/type-kind t)})
               {:name "Lacinia"}))
        friends (fn [context _ _]
                  (note :type (-> context root-type selection/type-name))
                  [{:name "Asinthe"}
                   {:name "Graphiti"}])
        schema (compile-sdl-schema "selection/object-type.sdl"
                                   {:Query/me me
                                    :Query/friends friends})]
    (is (= {:data {:me {:name "Lacinia"}}}
           (execute schema "{ me { name }}")))

    (is (= {:data {:friends [{:name "Asinthe"}
                             {:name "Graphiti"}]}}
           (execute schema "{ friends { name }}")))

    (is (= [[:type {:type-kind :object
                    :type-name :User}]
            ;; Still :User, because we unwrap list and non-null
            [:type :User]]
           @*facts))))

(deftest access-to-type-directives
  (let [me (fn [context _ _]
             (note :me-role (auth-role context))
             {:name "Lacinia"})
        friends (fn [context _ _]
                  (note :friends-role (auth-role context))
                  [{:name "Asinthe"}
                   {:name "Graphiti"}])
        schema (compile-sdl-schema "selection/object-type.sdl"
                                   {:Query/me me
                                    :Query/friends friends})]
    (is (= {:data {:me {:name "Lacinia"}}}
           (execute schema "{ me { name }}")))

    (is (= {:data {:friends [{:name "Asinthe"}
                             {:name "Graphiti"}]}}
           (execute schema "{ friends { name }}")))

    (is (= [[:me-role "basic"]
            [:friends-role "basic"]]
           @*facts))))

(deftest access-to-fields
  (let [me (fn [context _ _]
             (let [type (-> context
                            executor/selection
                            selection/root-value-type)
                   fields (selection/fields type)]
               (doseq [field (vals fields)]
                 (note :name (selection/qualified-name field)
                       :type (selection/root-type-name field)
                       :auth (some-> field
                                     selection/directives
                                     :auth
                                     first
                                     selection/arguments
                                     :role))))
             {:name "Lacinia"
              :department "not used"})
        schema (compile-sdl-schema "selection/object-type.sdl"
                                   {:Query/me me})]
    (is (= {:data {:me {:name "Lacinia"}}}
           (execute schema "{ me { name }}")))

    (is (= [[:name :User/name]
            [:type :String]
            [:auth "advanced"]
            [:name :User/department]
            [:type :String]
            [:auth nil]]
           @*facts))))


(deftest access-to-interface-directives
  (let [me (fn [context _ _]
             (let [t (root-type context)]
               (note :type-name (selection/type-name t)
                     :kind (selection/type-kind t)
                     :role (auth-role context)))
             (schema/tag-with-type {:name "Lacinia" :userId 101} :LegacyUser))
        schema (compile-sdl-schema "selection/interface-types.sdl"
                                   {:Query/me me})]
    (is (= {:data {:me {:name "Lacinia" :userId 101}}}
           (execute schema "
           {
             me {
                name
                ... on LegacyUser { userId }
             }
           }")))

    ;; And not the 'hidden' role on the LegacyUser object
    (is (= [[:type-name :User]
            [:kind :interface]
            [:role "basic"]]
           @*facts))))

(deftest sub-selections
  (let [me (fn [context _ _]
             (doseq [s (-> context
                           executor/selection
                           selection/selections)
                     :let [sub-kind (selection/selection-kind s)]]
               (note :sub-kind sub-kind)
               (when (= :field sub-kind)
                 (note :sub-field-name (selection/field-name s)
                       :sub-field-type (-> s selection/root-value-type selection/type-name))))

             (schema/tag-with-type {:name "Lacinia" :userId 101} :LegacyUser))
        schema (compile-sdl-schema "selection/interface-types.sdl"
                                   {:Query/me me})]
    (is (= {:data {:me {:type :LegacyUser
                        :name "Lacinia" :userId 101}}}
           (execute schema "
           {
             me {
                type: __typename
                name
                ... on LegacyUser { userId }
             }
           }")))

    (is (= [[:sub-kind :field]
            [:sub-field-name :__typename]
            [:sub-field-type :String]
            [:sub-kind :field]
            [:sub-field-name :name]
            [:sub-field-type :String]
            [:sub-kind :inline-fragment]]
           @*facts))))

(deftest access-to-union-directives
  (let [me (fn [context _ _]
             (let [t (root-type context)]
               (note :type-name (selection/type-name t)
                     :kind (selection/type-kind t)
                     :role (auth-role context)))
             (schema/tag-with-type {:userName "Lacinia" :userId 101}
                                   :LegacyUser))
        schema (compile-sdl-schema "selection/union-types.sdl"
                                   {:Query/me me})]
    (is (= {:data {:me {:userName "Lacinia" :userId 101}}}
           (execute schema "
           {
             me {
                ... on LegacyUser { userName userId }
             }
           }")))

    ;; And not the 'hidden' role on the LegacyUser object
    (is (= [[:type-name :User]
            [:kind :union]
            [:role "basic"]]
           @*facts))))

(deftest access-to-enum-directives
  (let [me (constantly
             {:name "Lacinia"})
        rank (fn [context _ _]
               (let [t (root-type context)]
                 (note :type-name (selection/type-name t)
                       :role (auth-role context))
                 :SENIOR))
        schema (compile-sdl-schema "selection/enum-types.sdl"
                                   {:Query/me me
                                    :User/rank rank})]

    (is (= {:data {:me {:name "Lacinia"
                        :rank :SENIOR}}}
           (execute schema "{ me { name rank } }")))

    (is (= [[:type-name :Rank]
            [:role "enum"]]
           @*facts))))

(deftest access-to-scalar-directives
  (let [me (constantly
             {:name "Lacinia"})
        uuid (str (UUID/randomUUID))
        id-resolver (fn [context _ _]
                      (note :type-name (-> context root-type selection/type-name)
                            :role (auth-role context))
                      uuid)
        schema (-> "selection/scalar-types.sdl"
                   io/resource
                   slurp
                   parse-schema
                   (util/inject-scalar-transformers {:UUID {:parse identity
                                                            :serialize identity}})
                   (util/inject-resolvers {:Query/me me
                                           :User/id id-resolver})
                   schema/compile)]

    (is (= {:data {:me {:name "Lacinia"
                        :id uuid}}}
           (execute schema "{ me { name id } }")))

    (is (= [[:type-name :UUID]
            [:role "basic"]]
           @*facts))))

(deftest directive-args
  (let [f (fn [context _ _]
            (let [limit (->> context
                             executor/selection
                             selection/directives
                             :limit
                             first
                             selection/arguments
                             :value)]
              (note :limit limit)
              (repeat limit "X")))
        schema (compile-sdl-schema "selection/directive-args.sdl"
                                   {:Query/basic f})]
    (is (= {:data {:basic (repeat 10 "X")}}
           (execute schema "{basic @limit}")))

    (is (= {:data {:basic (repeat 2 "X")}}
           (execute schema "{basic @limit(value: 2)}")))

    (is (= {:data {:basic ["X"]}}
           (execute schema "
           query($n: Int!) {
             basic @limit(value: $n)
           }"
                    {:n 1}
                    nil)))

    (is (= [[:limit 10]
            [:limit 2]
            [:limit 1]]
           @*facts))))
