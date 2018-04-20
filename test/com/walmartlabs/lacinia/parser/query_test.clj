(ns com.walmartlabs.lacinia.parser.query-test
  "Tests the low-level query parser."
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.walmartlabs.test-utils :refer [compile-schema execute]]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.parser :as parser]
    [com.walmartlabs.lacinia.parser.query :refer [parse-query]]
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(def ^:private compiled-schema
  (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver}))

;; Older tests do it the hard way: parse the query w/ a compiled schema, execute, and check
;; the results. That's more like an integration test, and is valid.

(defn ^:private ops
  [query]
  (->> query
       (parser/parse-query compiled-schema)
       (parser/operations)))

(defn ^:private args
  [query]
  (->> query
       (parser/parse-query compiled-schema)
       (:selections)
       (first)
       (:arguments)))

(deftest single-query
  (is (= {:operations #{:hero}
          :type :query}
         (ops "{ hero { name }}"))))

(deftest multiple-operations
  (is (= {:operations #{:hero
                        :human}
          :type :query}
         (ops "{ luke: hero { name }
                 leia: human { name }}"))))

(deftest mutations
  (is (= {:operations #{:changeHeroHomePlanet}
          :type :mutation}
         (ops "mutation { changeHeroHomePlanet(id: \"1234\", newHomePlanet: \"Gallifrey\") {
               name
             }
           }"))))

(deftest string-value-escape-sequences
  (testing "ascii"
    (is (= {:id "1234"
            :newHomePlanet "A \"great\"\nplace\\	?"}
           (args "mutation { changeHeroHomePlanet(id: \"1234\", newHomePlanet: \"A \\\"great\\\"\\nplace\\\\\\t?\") {name}}"))))

  (testing "unicode"
    (is (= {:id "1138"
            :newHomePlanet "❄ＨＯＴＨ❄"}
           (args "mutation { changeHeroHomePlanet(id: \"1138\", newHomePlanet: \"\\u2744\\uff28\\uff2f\\uff34\\uff28\\u2744\") {name}}")))))

(deftest query-reserved-word
  ;; Use 'query', 'mutation', and 'subscription' in various unusual places.
  (let [schema (compile-schema "query-reserved.edn"
                               {:resolve-query (fn [_ args _]
                                                 args)})

        result (execute schema "{ query(mutation: true) { mutation }}")]
    (is (= {:data {:query {:mutation true}}}
           result))))

(deftest requires-compiled-schema
  (is (thrown-with-msg? IllegalStateException
                        #"The provided schema has not been compiled"
                        (execute {} "{ whatever }"))))

;; These more recent tests are for testing the Antlr parser directly, w/ expectations of
;; a parsed query (the intermediate format).  Elsewhere, parsed query is the result of combining
;; the intermediate format with a compiled schema to prepare for execution. The term "executable query"
;; would be better, but there's a lot of history.

(defn ^:private content
  [base-name extension]
  (-> (str "parser/" base-name "." extension)
      io/resource
      (or (throw (IllegalStateException. (str "Source not found: " base-name "." extension))))
      slurp))

(defmacro ^:private expect
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

  (expect "operation-directives" "directives on operations")

  (expect "variable-defaults" "default for variables")

  (expect "named-operation" "names for operations"))

(deftest token-location-meta
  ;; Spot check that all the key elements, those that will likely have
  ;; a runtime error associated with them, have line/column metadata.
  (let [parsed (expect "fragment-directives" "parse worked")]

    ;; Operation
    (is (= {:line 1
            :column 1}
           (-> parsed first meta)))

    ;; Field
    (is (= {:column 3
            :line 2}
           (-> parsed first :selections first meta)))

    ;; Inline Fragment
    (is (= {:column 12                                      ; the type name
            :line 3}
           (-> parsed first :selections first :selections first meta)))

    ;; Named fragment
    (is (= {:column 9                                       ; the type name
            :line 5}
           (-> parsed first :selections first :selections (nth 2) meta)))

    (is (= {:column 10                                      ; the fragment name
            :line 9}
           (-> parsed second meta)))))
