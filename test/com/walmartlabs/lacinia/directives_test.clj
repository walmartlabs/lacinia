(ns com.walmartlabs.lacinia.directives-test
  (:require  [clojure.test :as t :refer [deftest is testing use-fixtures]]
             [com.walmartlabs.lacinia.schema :as schema]
             [com.walmartlabs.test-schema :refer [test-schema]]
             [com.walmartlabs.lacinia :refer [execute]]))

;;-------------------------------------------------------------------------------
;; ## Validation of directives (TODO)

(defn misplaced-directives
  [compiled-schema]
  (let [q "query Foo @include(if: true) {
             name @onQuery
             ...Frag @onQuery
           }
           mutation Bar @onQuery {
             someField
           }"]
    (execute compiled-schema q {} nil)))

(defn unknown-directives
  [compiled-schema]
  (let [q "{query {
             human(id: \"1000\") {
               human @unknown(directive: \"value\") {
                 name
               }
             }
            }"]
    (execute compiled-schema q {} nil)))

;;-------------------------------------------------------------------------------
;; ## Execution of directives

(def ^:dynamic compiled-schema)

(use-fixtures :once
              (fn [f]
                (binding [compiled-schema (schema/compile test-schema)]
                  (f))))

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
      (is (= {:data nil}
             (execute compiled-schema q {:skip true} nil))
          "should return no data")
      (is (= {:data {:human {:id "1001"}}}
             (execute compiled-schema q {:skip false} nil))
          "should return data"))))
