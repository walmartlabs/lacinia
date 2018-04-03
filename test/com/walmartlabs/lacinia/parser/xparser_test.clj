(ns com.walmartlabs.lacinia.parser.xparser-test
  "Tests for the Java intermediate GraphQL parser."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.parser.query :refer [parse-query]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]))


(defn content
  [base-name extension]
  (-> (str "parser/" base-name "." extension)
      io/resource
      (or (throw (IllegalStateException. (str "Source not found: " base-name "." extension))))
      slurp))

(defmacro expect
  [base-name message]
  `(let [input# (content ~base-name "gql")
         expected# (edn/read-string (content ~base-name "edn"))
         actual# (parse-query input#)]
     (is (= actual#
            expected#)
         ~message)
     actual#))

(deftest basics
  (expect "simple" "minimal query"))

(deftest basics2
  (expect "nested-fields" "nested fields below the root"))

(deftest basics3
  (expect "aliases" "identifies aliases"))

(deftest basics4
  (expect "explicit-query" "use of the optional query keyword"))

(deftest basics5
  (expect "reserved-words" "handles reserved words as field names properly"))
