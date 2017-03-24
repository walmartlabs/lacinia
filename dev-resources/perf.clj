(ns perf
  "A namespace where we track relative performance of query parsing and execution."
  (:require
    [org.example.schema :refer [star-wars-schema]]
    [criterium.core :as c]
    [com.walmartlabs.lacinia :refer [execute execute-parsed-query]]
    [com.walmartlabs.lacinia.parser :as parser]))

;; Be aware that any change to this schema will invalidate any gathered
;; performance data.

(def compiled-schema (star-wars-schema))

;; This is the standard introspection query that graphiql
;; executes to build the client-side UI.

(def query-raw "query IntrospectionQuery {
            __schema {
              queryType { name }
              mutationType { name }
              types {
                ...FullType
              }
              directives {
                name
                description
                args {
                  ...InputValue
                }
              }
            }
          }
          fragment FullType on __Type {
            kind
            name
            description
            fields(includeDeprecated: true) {
              name
              description
              args {
                ...InputValue
              }
              type {
                ...TypeRef
              }
              isDeprecated
              deprecationReason
            }
            inputFields {
              ...InputValue
            }
            interfaces {
              ...TypeRef
            }
            enumValues(includeDeprecated: true) {
              name
              description
              isDeprecated
              deprecationReason
            }
            possibleTypes {
              ...TypeRef
            }
          }
          fragment InputValue on __InputValue {
            name
            description
            type { ...TypeRef }
            defaultValue
          }
          fragment TypeRef on __Type {
            kind
            name
            ofType {
              kind
              name
              ofType {
                kind
                name
                ofType {
                  kind
                  name
                }
              }
            }
          }")

(def parsed-query (parser/parse-query compiled-schema query-raw))

(defn query-parse-time
  []
  (let [results (c/benchmark (parser/parse-query compiled-schema query-raw)
                             nil)]
    (println "Query parse time:")
    (c/report-result results)
    results))

(defn query-execution-time
  []
  (let [results (c/benchmark (execute-parsed-query parsed-query nil nil)
                             nil)]
    (println "Query execution time:")
    (c/report-result results)
    results))

(comment
  (do (query-parse-time) nil)
  (do (query-execution-time) nil)

  )

;; Execution notes (not very scientific):

;; Date     - Who    - Clojure - Parse - Execute
;; 20161104 - hlship - 1.9a13  -  1.68 - 2.77
;; 20161108 - hlship - 1.9a13  -  1.75 - 3.3
;; 20161123 - hlship - 1.9a14  -  1.74 - 2.91
;; 20170113 - hlship - 1.9a14  -  1.68 - 2.92
;; 20170209 - hlship - 1.9a14  -  2.00 - 3.72
;; 20170209 - hlship - 1.9a14  -  2.16 - 2.50
;; 20170213 - hlship - 1.9a14  -  1.99 - 5.11
;; 20170213 - hlship - 1.9a14 -   2.05 - 5.17
;; 20170213 - hlship - 1.9a14 -   2.00 - 7.71
;; 20170213 - hlship - 1.9a14 -   2.02 - 4.79
;; 20170224 - hlship - 1.9a14 -   2.07 - 3.42 (introspection rewrite)
;; 20170310 - hlship - 1.8    -   2.02 - 3.62 (revert to 1.8, use future.spec)


;; Goal: Identify *glaring* changes in performance of query parse
;; or query execution. Likely affected by what else is going on
;; the computer during benchmark execution.
