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

(deftest general-parsing
  (expect "simple" "minimal query")

  (expect "nested-fields" "nested fields below the root")

  (expect "aliases" "identifies aliases")

  (expect "explicit-query" "use of the optional query keyword")

  (expect "reserved-words" "handles reserved words as field names properly")

  (expect "args" "basic field arguments support")

  (expect "literals" "core set of literal values")

  (expect "arrays" "arrays of simple literals")

  (expect "enum" "enum literal values")

  (expect "enum-reserved" "enums as reserved words")

  (expect "object" "structured objects")

  (expect "reserved-args" "argument names can be a reserved word")

  (expect "vars" "variables in query")

  (expect "frag-spread" "inline ... fragment syntax ")

  (expect "named-fragment" "named fragment define and use")

  (expect "field-directive" "directives on fields")

  (expect "fragment-directives" "directives on inline and named fragments")

  (expect "operation-directives" "directives on operations"))

;; TODO
;; - duplicate arg name for same field
;; - duplicate property name for same object
;; - location metadata carries forward


