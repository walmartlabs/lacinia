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

(ns com.walmartlabs.lacinia.resolve-test
  "Tests to ensure that field resolvers are passed expected values."
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia :as graphql :refer [execute]]
    [com.walmartlabs.test-utils :refer [compile-schema] :as tu]
    [clojure.walk :refer [postwalk]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.selection :as sel]))

(def resolve-contexts (atom []))

(defn ^:private instrument-and-compile
  [schema]
  (->> schema
       (postwalk (fn [node]
                   (if-let [default-resolve (:resolve node)]
                     (assoc node :resolve
                                 (fn [context args value]
                                   (swap! resolve-contexts conj context)
                                   (default-resolve context args value)))
                     node)))
       schema/compile))

(def compiled-schema (instrument-and-compile test-schema))

;; Ensure that resolve-contexts is reset to empty after each test execution.

(use-fixtures
  :each
  (fn [f]
    (f)
    (swap! resolve-contexts empty)))


(deftest passes-root-query-to-resolve
  (let [q "query {
             human(id: \"1000\") {
               name
             }
           }"
        query-result (execute compiled-schema q nil {::my-key ::my-value})
        [c1 :as contexts] @resolve-contexts]

    (is (= {:data {:human {:name "Luke Skywalker"}}}
           query-result))

    ;; Just to verify that user context is passed through.

    (is (= ::my-value (::my-key c1)))

    ;; Only the resolve for the [:query :human] in thise case, as :name is a simple
    ;; default resolve (added during compilation).

    (is (= 1 (count contexts)))

    (is (= :human (-> c1 ::graphql/selection :field-name)))

    (is (= "human!"
           (-> c1 ::graphql/selection sel/field sel/kind sel/as-type-string)))

    ;; This is pretty important: can we see what else will be queried?
    ;; We're focusing in these tests on sub-fields with the root query field.

    (is (= 1 (-> c1 ::graphql/selection :selections count)))

    (is (= {:field-name :name
            :alias :name}
           (-> c1 ::graphql/selection :selections first (select-keys [:field-name :alias]))))))

(deftest passes-nested-selections-to-resolve
  (let [q "query { human(id: \"1000\") { buddies: friends { name }}}"
        query-result (execute compiled-schema q nil nil)
        [c1 c2 :as contexts] @resolve-contexts]
    (is (= {:data
            {:human
             {:buddies
              [{:name "Han Solo"}
               {:name "Leia Organa"}
               {:name "C-3PO"}
               {:name "R2-D2"}]}}}
           query-result))

    ;; Two: the resolve for the human query, and the resolve for the
    ;; nested friends field.

    (is (= 2 (count contexts)))

    ;; This is important; the upper resolves get a preview of what's going on with the
    ;; lower resolves. In theory, a database oriented query could use this to build a richer
    ;; query at the top resolve that "seeds" data that will simply be extracted by lower resolves.

    (is (= (-> c1 :selection :selections first)
           (:selection c2)))

    (is (= {:field-name :friends
            :alias :buddies}
           (-> c2 ::graphql/selection (select-keys [:field-name :alias]))))))

(deftest checks-that-bare-values-are-wrapped-as-a-tuple
  (let [return-value "What, me worry?"
        schema (schema/compile {:queries {:catchphrase {:type :String
                                                        :resolve (constantly return-value)}}})]
    (is (= {:data {:catchphrase return-value}}
           (graphql/execute schema "{catchphrase}" nil nil))) 1))

(deftest field-resolver-protocol
  (let [resolver (reify resolve/FieldResolver
                   (resolve-value [_ _ _ _]
                     "Like magic!"))
        schema (compile-schema "field-resolver-protocol-schema.edn"
                               {:query/hello resolver})]
    (is (= {:data {:hello "Like magic!"}}
           (tu/execute schema "{ hello }")))))

(deftest default-resolver-return-resolver-result
  (let [data-resolver (fn [_ _ _]
                        {:value (resolve/resolve-as "Ahsoka Tano")})
        schema (schema/compile (-> {:objects
                                    {:Data
                                     {:fields
                                      {:value {:type :String}}}}
                                    :queries
                                    {:data {:type :Data}}}
                                   (util/inject-resolvers {:queries/data data-resolver})))]
    (is (= {:data {:data {:value "Ahsoka Tano"}}}
           (tu/execute schema "{ data { value } }")))))