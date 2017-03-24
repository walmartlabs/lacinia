(ns perf
  "A namespace where we track relative performance of query parsing and execution."
  (:require
    [incanter.core :refer :all]
    [incanter.datasets :refer :all]
    [incanter.io :refer :all]
    [incanter.charts :refer :all]
    [org.example.schema :refer [star-wars-schema]]
    [criterium.core :as c]
    [com.walmartlabs.lacinia :refer [execute execute-parsed-query]]
    [com.walmartlabs.lacinia.parser :as parser]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [clojure.tools.cli :as cli])
  (:import (java.util Date)))

;; Be aware that any change to this schema will invalidate any gathered
;; performance data.

(def compiled-schema (star-wars-schema))

;; This is the standard introspection query that graphiql
;; executes to build the client-side UI.

(def introspection-query-raw
  "query IntrospectionQuery {
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

(def ^:private benchmark-queries
  {:introspection
   {:query introspection-query-raw}
   :basic
   {:query "
   {
     default: human {
       name
       appears_in
       friends { name }
       home_planet
     }
     hope_hero: hero(episode: NEWHOPE) {
       id
       name
       friends { name }}
   }"}
   :basic-vars
   {:query "
   query ($ep : episode!) {
     default: human {
       name
       appears_in
       friends { name }
       home_planet
     }
     hope_hero: hero(episode: $ep) {
       id
       name
       friends { name }
     }
   }"
    :vars {:ep "NEWHOPE"}}})

(defmacro ^:private benchmark [expr]
  `(-> ~expr
       (c/benchmark nil)
       c/with-progress-reporting
       :mean
       first
       ;; It's in seconds, scale up to ms
       (* 1000.)))

(defn ^:private run-benchmark
  "Runs the benchmark, returns a tuple of the benchmark name, the mean parse time (in ms),
   and the mean execution time (in ms)."
  [benchmark-name]
  (println "Running benchmark" (name benchmark-name) "(parse) ...")

  (let [{query-string :query
         variables :vars} (get benchmark-queries benchmark-name)
        parse-time (benchmark (parser/parse-query compiled-schema query-string))
        parsed-query (parser/parse-query compiled-schema query-string)
        _ (println "Running benchmark" (name benchmark-name) "(exec) ...")
        exec-time (benchmark (execute-parsed-query parsed-query variables nil))]
    [(name benchmark-name) parse-time exec-time]))

(defn ^:private create-charts
  [dataset options]
  (when (:print options)
    (prn dataset))
  (with-data dataset
    (-> (line-chart :date :parse
                    :title "Historical Parse Time / Operation"
                    :group-by :kind
                    :x-label "Date"
                    :y-label "ms"
                    :legend true)
        (save "perf/parse-time.png"
              :width 1000))
    (-> (line-chart :date :exec
                    :title "Historical Execution Time / Operation"
                    :group-by :kind
                    :x-label "Date"
                    :y-label "ms"
                    :legend true)
        (save "perf/exec-time.png"
              :width 1000))))

(defn ^:private git-commit
  []
  (-> (sh "git" "rev-parse" "HEAD")
      :out
      (or "UNKNOWN")
      str/trim
      (subs 0 8)))

(def ^:private dataset-file "perf/benchmarks.csv")

(defn run-benchmarks [options]
  (let [prefix [(format "%tY%<tm%<td" (Date.))
                (or (:commit options) (git-commit))]
        new-benchmarks (->> (map run-benchmark (keys benchmark-queries))
                            (map #(into prefix %)))
        dataset (-> (read-dataset dataset-file :header true)
                    (conj-rows new-benchmarks))]
    (create-charts dataset options)
    (when (:save options)
      (save dataset dataset-file)
      (println "Updated perf.csv"))))

(def ^:private cli-opts
  [["-s" "--save" "Update benchmark data file after running benchmarks."]
   ["-p" "--print" "Print the table of benchmark data used to generate charts."]
   ["-c" "--commit SHA" "Specify Git commit SHA; defaults to current commit (truncated to 8 chars)."]
   ["-h" "--help" "This usage summary."]])

(defn -main
  [& args]
  (let [{:keys [options errors summary]} (cli/parse-opts args cli-opts)
        usage (fn [errors]
                (println "lein benchmarks [options]")
                (println summary)

                (when (seq errors)
                  (println)
                  (run! println errors)))]
    (cond
      (or (:help options)
          (seq errors))
      (usage errors)

      :else
      (run-benchmarks options))))
