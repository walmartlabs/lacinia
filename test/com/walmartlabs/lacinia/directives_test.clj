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

(ns com.walmartlabs.lacinia.directives-test
  (:require
    [clojure.test :refer [deftest is testing]]
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia :refer [execute]]))

;;-------------------------------------------------------------------------------
;; ## Execution of directives

(def compiled-schema (schema/compile test-schema))

(deftest inline-fragments
  (testing "when @skip is set on an inline fragment"
    (let [q "query ($skip : Boolean!) {
               human(id: \"1000\") {
                 name
                 ... on human @skip(if: $skip) {
                   id
                 }
               }
             }"]
      (is (= {:data {:human {:name "Luke Skywalker"}}}
             (execute compiled-schema q {:skip true} nil))
          "should return name only")
      (is (= {:data {:human {:name "Luke Skywalker"
                             :id "1000"}}}
             (execute compiled-schema q {:skip false} nil))
          "should return both fields")))
  (testing "when @include is set on an inline fragment"
    (let [q "query ($include : Boolean!) {
               human(id: \"1000\") {
                 name
                 ... on human @include(if: $include) {
                   id
                 }
               }
             }"]
      (is (= {:data {:human {:name "Luke Skywalker"}}}
             (execute compiled-schema q {:include false} nil))
          "should return name only")
      (is (= {:data {:human {:name "Luke Skywalker"
                             :id "1000"}}}
             (execute compiled-schema q {:include true} nil))
          "should return both fields"))))

(deftest mixed-directives
  (testing "when both @skip and @include are set"
    (let [q "query ($skip: Boolean!, $include: Boolean!) {
               human(id: \"1000\") {
                 name
                 friends {
                   id @skip(if: $skip) @include(if: $include)
                 }
               }
             }"]
      (is (= {:data {:human {:name "Luke Skywalker"
                             ;; Here id was skipped, leaving an empty selection for
                             ;; each friend.
                             ;; This behavior is confirmed in the JavaScript reference implementation,
                             ;; but is ambiguous is the spec.
                             :friends [{} {} {} {}]}}}
             (execute compiled-schema q {:skip true :include true} nil)))
      (is (= {:data {:human {:name "Luke Skywalker"
                             :friends [{:id "1002"} {:id "1003"} {:id "2000"} {:id "2001"}]}}}
             (execute compiled-schema q {:skip false :include true} nil))
          "should return both fields")
      (is (= {:data {:human {:name "Luke Skywalker"
                             :friends [{} {} {} {}]}}}
             (execute compiled-schema q {:skip false :include false} nil))))))

(deftest fragment-spreads
  (testing "when @skip is set on a fragment spread"
    (let [q "query ($skip : Boolean!) {
               human(id: \"1000\") {
                 name
                 ...IdFrag
                 friends {
                   ...IdFrag @skip(if: $skip)
                 }
               }
             }
             fragment IdFrag on human {
               id
             }"]
      (is (= {:data {:human {:name "Luke Skywalker"
                             :id "1000"
                             :friends [{} {} {} {}]}}}
             (execute compiled-schema q {:skip true} nil))
          "should return name only")
      (is (= {:data {:human {:name "Luke Skywalker"
                             :id "1000"
                             :friends [{:id "1002"} {:id "1003"} {} {}]}}}
             (execute compiled-schema q {:skip false} nil))
          "should return all fields")))
  (testing "when @include is set on a fragment spread"
    (let [q "query ($include: Boolean!) {
               human(id: \"1000\") {
                 name
                 ...IdFrag @include(if: $include)
               }
             }
             fragment IdFrag on human {
               id
             }"]
      (is (= {:data {:human {:name "Luke Skywalker"}}}
             (execute compiled-schema q {:include false} nil))
          "should return name only")
      (is (= {:data {:human {:name "Luke Skywalker"
                             :id "1000"}}}
             (execute compiled-schema q {:include true} nil))
          "should return both fields")))
  (testing "when @include is set on a fragment spread and there are no other fields selected"
    (let [q "query ($include: Boolean!) {
               human(id: \"1000\") {
                 ...IdFrag @include(if: $include)
               }
             }
             fragment IdFrag on human {
               id
             }"]
      ;; This indicates that a human was found, but that nothing was selected due to directives.
      (is (= {:data {:human {}}}
             (execute compiled-schema q {:include false} nil))
          "should return no data")
      (is (= {:data {:human {:id "1000"}}}
             (execute compiled-schema q {:include true} nil))
          "should return friends field"))))

(deftest simple-directives
  (testing "when @skip is set"
    (let [q "query ($skip: Boolean!) {
               human(id: \"1000\") {
                 name
                 friends {
                   id @skip(if: $skip)
                 }
               }
             }"]
      (is (= {:data {:human {:name "Luke Skywalker"
                             :friends [{} {} {} {}]}}}
             (execute compiled-schema q {:skip true} nil))
          "should return name only")
      (is (= {:data {:human {:name "Luke Skywalker"
                             :friends [{:id "1002"} {:id "1003"} {:id "2000"} {:id "2001"}]}}}
             (execute compiled-schema q {:skip false} nil))
          "should return both fields")))

  (testing "when @include is set"
    (let [q "query ($include: Boolean!) {
               human(id: \"1000\") {
                 name
                 friends {
                   id @include(if: $include)
                 }
               }
             }"]
      (is (= {:data {:human {:name "Luke Skywalker"
                             :friends [{:id "1002"} {:id "1003"} {:id "2000"} {:id "2001"}]}}}
             (execute compiled-schema q {:include true} nil))
          "should return both fields")
      (is (= {:data {:human {:name "Luke Skywalker"
                             :friends [{} {} {} {}]}}}
             (execute compiled-schema q {:include false} nil))
          "should return name only")))


  (testing "when @skip is set for the only field requested"
    (let [q "query ($skip: Boolean!) {
               human(id: \"1000\") {
                 name @skip(if: $skip)
               }
             }"]
      (is (= {:data {:human {}}}
             (execute compiled-schema q {:skip true} nil))
          "should return no data")
      (is (= {:data {:human {:name "Luke Skywalker"}}}
             (execute compiled-schema q {:skip false} nil))
          "should return data")))

  (testing "when @skip is set for the top level field"
    (let [q "query ($skip : Boolean!) {
               human @skip(if: $skip) {
                 id
               }
             }"]
      (is (= {:data {}}
             (execute compiled-schema q {:skip true} nil))
          "should return no data")
      (is (= {:data {:human {:id "1001"}}}
             (execute compiled-schema q {:skip false} nil))
          "should return data"))))

;; Validation of directives and elements using directives.

(defn ^:private merge-exception-data
  ([e]
   (merge-exception-data e (ex-data e)))
  ([^Throwable e data]
   (let [next-e (.getCause e)]
     (if (or (nil? next-e)
             (identical? e next-e))
       ;; This just makes the test verbose, so it's removed:
       (dissoc data :schema-types)
       (merge-exception-data next-e (merge data (ex-data e)))))))

(defmacro directive-test
  [expected-msg expected-ex-data schema]
  `(when-let [e# (is (~'thrown? Throwable (schema/compile ~schema)))]
     (is (= ~expected-msg (.getMessage e#)))
     (is (= ~expected-ex-data
            (merge-exception-data e#)))))


(deftest unknown-argument-type-for-directive
  (directive-test
    "Unknown argument type."
    {:arg-name :date
     :arg-type-name :Date}
    {:directive-defs
     {:Ebb {:locations #{:enum}
            :args {:date {:type :Date}}}}}))

(deftest incorrect-argument-type-for-directive
  (directive-test
    "Directive argument is not a scalar type."
    {:arg-name :user
     :arg-type-name :User}
    {:directive-defs {:Ebb {:locations #{:enum}
                            :args {:user {:type :User}}}}
     :objects
     {:User
      {:fields
       {:name {:type :String}}}}}))

(deftest object-directive-unknown-type
  (directive-test
    "Object `User' references unknown directive @Unknown."
    {:directive-type :Unknown
     :object :User}
    {:objects
     {:User {:directives [{:directive-type :Unknown}]
             :fields {}}}}))

(deftest object-directive-inapplicable
  (directive-test
    "Directive @Ebb on object `Flow' is not applicable."
    {:allowed-locations #{:enum}
     :directive-type :Ebb
     :object :Flow}
    {:directive-defs
     {:Ebb {:locations #{:enum}}}
     :objects
     {:Flow {:fields {}
             :directives [{:directive-type :Ebb}]}}}))

(deftest union-directive-unknown-type
  (directive-test
    "Union `Ebb' references unknown directive @Unknown."
    {:directive-type :Unknown
     :union :Ebb}
    {:objects
     {:Flow {:fields {}}}
     :unions
     {:Ebb {:members [:Flow]
            :directives [{:directive-type :Unknown}]}}}))

(deftest union-directive-inapplicable
  (directive-test
    "Directive @deprecated on union `Ebb' is not applicable."
    {:allowed-locations #{:enum-value
                          :field-definition}
     :directive-type :deprecated
     :union :Ebb}
    {:objects
     {:Flow {:fields {}}}
     :unions
     {:Ebb {:members [:Flow]
            ;; Can only deprecate fields and enum values
            :directives [{:directive-type :deprecated}]}}}))

(def ^:private mock-conformer (schema/as-conformer identity))

(deftest scalar-directive-unknown-type
  (directive-test
    "Scalar `Ebb' references unknown directive @Unknown."
    {:directive-type :Unknown
     :scalar :Ebb}
    {:scalars
     {:Ebb {:parse mock-conformer
            :serialize mock-conformer
            :directives [{:directive-type :Unknown}]}}}))

(deftest scalar-directive-inapplicable
  (directive-test
    "Directive @deprecated on scalar `Ebb' is not applicable."
    {:allowed-locations #{:enum-value
                          :field-definition}
     :directive-type :deprecated
     :scalar :Ebb}
    {:scalars
     {:Ebb {:parse mock-conformer
            :serialize mock-conformer
            ;; Can only deprecate fields and enum values
            :directives [{:directive-type :deprecated}]}}}))

(deftest field-directive-unknown-type
  (directive-test
    "Field `User/id' references unknown directive @Unknown."
    {:directive-type :Unknown
     :field-name :User/id}
    {:objects
     {:User
      {:fields {:id {:type :String
                     :directives [{:directive-type :Unknown}]}}}}}))

(deftest field-directive-inapplicable
  (directive-test
    "Directive @Ebb on field `Flow/id' is not applicable."
    {:allowed-locations #{:enum}
     :directive-type :Ebb
     :field-name :Flow/id}
    {:directive-defs
     {:Ebb {:locations #{:enum}}}
     :objects
     {:Flow
      {:fields
       {:id {:type :String
             :directives [{:directive-type :Ebb}]}}}}}))

(deftest argument-directive-unknown-type
  (directive-test
    "Argument `ebb' of field `User/id' references unknown directive @Unknown."
    {:directive-type :Unknown
     :arg-name :ebb
     :field-name :User/id}
    {:objects
     {:User
      {:fields
       {:id {:type :String
             :args {:ebb {:type :String
                          :directives [{:directive-type :Unknown}]}}}}}}}))

(deftest argument-directive-inapplicable
  (directive-test
    "Directive @Ebb on argument `format' of field `Flow/id' is not applicable."
    {:allowed-locations #{:enum}
     :directive-type :Ebb
     :arg-name :format
     :field-name :Flow/id}
    {:directive-defs
     {:Ebb {:locations #{:enum}}}
     :objects
     {:Flow
      {:fields
       {:id {:type :String
             :args
             {:format {:type :String
                       :directives [{:directive-type :Ebb}]}}}}}}}))

(deftest enum-directive-unknown-type
  (directive-test
    "Enum `Colors' references unknown directive @Unknown."
    {:directive-type :Unknown
     :enum :Colors}
    {:enums
     {:Colors
      {:values [:red :green :blue]
       :directives [{:directive-type :Unknown}]}}}))

(deftest enum-directive-inapplicable
  (directive-test
    "Directive @nope on enum `Colors' is not applicable."
    {:allowed-locations #{:object}
     :directive-type :nope
     :enum :Colors}
    {:directive-defs {:nope {:locations #{:object}}}
     :enums
     {:Colors
      {:values [:red :green :blue]
       :directives [{:directive-type :nope}]}}}))

(deftest enum-value-directive-unknown-type
  (directive-test
    "Enum value `Colors/red' referenced unknown directive @Unknown."
    {:directive-type :Unknown
     :enum-value :Colors/red}
    {:enums
     {:Colors
      {:values [{:enum-value :red
                 :directives [{:directive-type :Unknown}]}
                :green :blue]}}}))




(comment
  (require '[clojure.test :refer [run-tests]])
  (run-tests)
  )
