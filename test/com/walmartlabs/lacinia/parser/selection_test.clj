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
  "Tests related to the executor/selection functi5on and related data, introduced in 0.38.0."
  (:require
    [clojure.test :refer [is deftest use-fixtures]]
    [com.walmartlabs.lacinia.executor :as executor]
    [com.walmartlabs.lacinia.protocols :as p]
    [com.walmartlabs.test-utils :refer [compile-sdl-schema execute]]
    [com.walmartlabs.lacinia.schema :as schema]))

(def ^:private *log (atom []))

(defn ^:private log
  [& kvs]
  (let [pairs (partition-all 2 kvs)]
    (swap! *log into pairs)))

(defn ^:private reset-log
  [f]
  (reset! *log [])
  (f))

(use-fixtures :each reset-log)

(deftest access-to-selection
  (let [f (fn [context _ _]
            (let [s (executor/selection context)
                  directives (p/directives s)]
              (log :selection {:field-selection? (p/field-selection? s)
                               :qualified-name (p/qualified-name s)
                               :field-name (p/field-name s)
                               :alias (p/alias-name s)}
                   :directive-keys (-> directives keys sort)
                   :directive-names (->> directives
                                         :concise
                                         (map p/directive-type))))
            "Done")
        schema (compile-sdl-schema "selection/simple.sdl"
                                   {:Query/basic f})
        result (execute schema "{ basic @concise }")]
    (is (= {:data {:basic "Done"}}
           result))
    (is (= '[[:selection {:field-selection? true
                          :qualified-name :Query/basic
                          :field-name :basic
                          :alias :basic}]
             [:directive-keys [:concise]]
             [:directive-names [:concise]]]
           @*log))))

(deftest access-to-type
  (let [me (fn [context _ _]
             (let [t (-> context
                         executor/selection
                         p/root-value-type)]
               (log :type {:type-name (p/type-name t)
                           :type-kind (p/type-kind t)})
               {:name "Lacinia"}))
        friends (fn [context _ _]
                  (log :type (-> context
                                 executor/selection
                                 p/root-value-type
                                 p/type-name))
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
           @*log))))

(deftest access-to-type-directives
  (let [context->role (fn [context]
                        (-> context
                            executor/selection
                            p/root-value-type
                            p/directives
                            :auth
                            first
                            p/arguments
                            :role))
        me (fn [context _ _]
             (log :me-role (context->role context))
             {:name "Lacinia"})
        friends (fn [context _ _]
                  (log :friends-role (context->role context))
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
           @*log))))

(deftest access-to-interface-directives
  (let [me (fn [context _ _]
             (let [root-type (-> context
                                 executor/selection
                                 p/root-value-type)]
               (log :type-name (p/type-name root-type)
                    :kind (p/type-kind root-type)
                    :role (-> root-type
                              p/directives
                              :auth
                              first
                              p/arguments
                              :role)))
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
           @*log))))

(deftest access-to-union-directives
  (let [me (fn [context _ _]
             (let [root-type (-> context
                                 executor/selection
                                 p/root-value-type)]
               (log :type-name (p/type-name root-type)
                    :kind (p/type-kind root-type)
                    :role (-> root-type
                              p/directives
                              :auth
                              first
                              p/arguments
                              :role)))
             (schema/tag-with-type {:userName "Lacinia" :userId 101} :LegacyUser))
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
           @*log))))

(deftest directive-args
  (let [f (fn [context _ _]
            (let [limit (->> context
                             executor/selection
                             p/directives
                             :limit
                             first
                             p/arguments
                             :value)]
              (log :limit limit)
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
           @*log))))

(comment
  (-> "selection/interface-types.sdl"
      clojure.java.io/resource
      slurp
      com.walmartlabs.lacinia.parser.schema/parse-schema)
  )