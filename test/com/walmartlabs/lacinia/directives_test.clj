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
  (:require [clojure.test :as t :refer [deftest is testing]]
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
