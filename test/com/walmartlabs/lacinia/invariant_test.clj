(ns com.walmartlabs.lacinia.invariant-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.parser :refer [parse-query invariant? prepare-with-query-variables]]
            [com.walmartlabs.test-schema :refer [test-schema]]
            [com.walmartlabs.test-utils :as utils :refer [simplify]]))

(def ^:private compiled-schema (schema/compile test-schema))

(deftest invariant-query-is-unchanged-by-prepare
  (let [parsed (parse-query compiled-schema "{ hero { name } }")
        prepared (prepare-with-query-variables parsed nil)]
    (is (invariant? parsed))
    (is (identical? parsed prepared))))

(defmacro expect-invariant
  [q]
  `(is (= true (invariant? (parse-query compiled-schema ~q)))))

(defmacro expect-variant
  [q]
  `(is (= false (invariant? (parse-query compiled-schema ~q)))))

(deftest field-args-do-not-trigger-variant
  (expect-invariant "{ hero (episode: JEDI) { name }}"))

(deftest fragments-do-not-trigger-variant
  (expect-invariant "
  {
    hero { ...character }
  }

  fragment character on character { id name }
  "))

(deftest skip-or-include-are-variant
  (expect-variant "{ hero { id name @skip(if: true) } }")

  (expect-variant "
  {
    hero { ...character }
  }

  fragment character on character { id @skip(if: false) name }"))

(deftest unused-variables-are-invariant
  (expect-invariant
    "query ($id: String) {
      hero { name }
    }"))

(deftest used-variables-are-variant
  (expect-variant
    "query ($ep: episode) {
      hero(episode: $ep) { name }
    }")


  (expect-variant "
  query($ep: episode) { ...heroQuery }

  fragment heroQuery on Query {
    hero(episode: $ep) { name }
  }

  "))






