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

(deftest basics6
  (expect "args" "basic field arguments support"))

(deftest basic7
  (expect "literals" "core set of literal values"))

(deftest basic8
  (expect "arrays" "arrays of simple literals"))

(deftest basic9
  (expect "enum" "enum literal values"))

(deftest basic10
  (expect "enum-reserved" "enums as reserved words"))

(deftest x11
  (expect "object" "structured objects"))

(deftest x12
  (expect "reserved-args" "argument names can be a reserved word"))

(deftest x13
  (expect "vars" "variables in query"))

(deftest x14
  (expect "frag-spread" "inline ... fragment syntax "))

(deftest x15
  (expect "named-fragment" "named fragment define and use"))

(deftest x16
  (expect "field-directive" "directives on fields"))

(deftest x17
  (expect "fragment-directives" "directives on inline and named fragments"))

(deftest x18
  (expect "operation-directives" "directives on operations"))

;; TODO
;; - duplicate arg name for same field
;; - duplicate property name for same object
;; - location metadata carries forward


