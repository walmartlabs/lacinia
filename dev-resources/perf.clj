(ns perf
  "A namespace where we track relative performance of query parsing and execution."
  (:require
    [org.example.schema :refer [star-wars-schema]]
    [criterium.core :as c]
    [com.walmartlabs.lacinia :refer [execute execute-parsed-query]]
    [com.walmartlabs.lacinia.parser.schema :refer [parse-schema]]
    [com.walmartlabs.lacinia.parser :as parser]
    [clojure.java.shell :refer [sh]]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.data.json :as json]
    [clojure.data.csv :as csv]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
    [com.walmartlabs.lacinia.executor :as executor]
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [com.walmartlabs.test-utils :refer [simplify]]
    [clojure.edn :as edn])
  (:import
    (java.util Date)
    (java.util.concurrent ThreadPoolExecutor TimeUnit SynchronousQueue)))

(defn -defeat-linter
  []
  executor/selections-seq)

;; Be aware that any change to this schema will invalidate any gathered
;; performance data.

(def compiled-star-wars-schema (star-wars-schema))

(defn ^:private read-edn
  [file]
  (or (some-> (io/resource file)
              slurp
              edn/read-string)
      (throw (ex-info "File not found" {:file file}))))

(defn ^:private read-json
  [file]
  (or (some-> file
              io/resource
              slurp
              (json/read-str :key-fn keyword))))

(def compiled-deep-schema
  (-> "deep.sdl"
       io/resource
       slurp
       parse-schema
        (util/inject-resolvers {:Query/getResource (constantly
                                                    (read-json "StructureDefinition-us-core-pulse-oximetry.json"))})
       schema/compile))

;; A schema to measure the performance of errors
(def planets-schema
  (let [planet-data [{:name "Mercury"}
                     {:name "Venus"}
                     {:name "Earth"
                      :moons [{:name "Luna"
                               :bases [{:name "Alpha"}
                                       {:name "Moon 1"}
                                       {:name "月基地图"}]}]}
                     {:name "Mars"
                      :moons [{:name "Phobos"}
                              {:name "Deimos"}]}
                     {:name "Asteroid Belt"
                      :moons (for [i (range 1 101)]
                               {:name (str "Asteroid " i)
                                :bases [{:name "Pad"
                                         :destroyed? true}]})}
                     {:name "Jupiter"
                      :moons [{:name "Europa"
                               :bases [{:name "Beta"
                                        :alien? true}]}
                              {:name "Ganymede"}
                              {:name "Callisto"
                               :bases [{:name "Gamma"}]}]}
                     {:name "Saturn"
                      :moons [{:name "Dione"}
                              {:name "Tethys"}
                              {:name "Titan"
                               :bases [{:name "Omega"}]}]}
                     {:name "Saturn"}
                     {:name "Uranus"}]
        resolvers {:base-name (fn [_ _ base]
                                (resolve-as (:name base)
                                            (cond
                                              (:alien? base)
                                              {:message "Europa is forbidden. Attempt no landing there."}

                                              (:destroyed? base)
                                              {:message "This base has been destroyed."})))
                   :planets (fn [_ _ _]
                              planet-data)}]
    (-> '{:objects
          {:planet
           {:fields
            {:name {:type (non-null String)}
             :moons {:type (list :moon)}}}

           :moon
           {:fields
            {:name {:type (non-null String)}
             :bases {:type (list :base)}}}

           :base
           {:fields
            {:name {:type (non-null String)
                    :resolve :base-name}}}}

          :queries
          {:planets
           {:type (list :planet)
            :resolve :planets}}}
        (util/attach-resolvers resolvers)
        schema/compile)))

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
   {:query introspection-query-raw
    :expected (read-edn "introspection.edn")}

   ;; :deep uses a single resolver that delivers the entire
   ;; deeply nested map; it uses lots of fragments and the
   ;; schema is both broad and deep; this hopefully resembles
   ;; the runtime behavior for our bigger Walmart queries.
   :deep
   {:schema compiled-deep-schema
    :query "
   query {
      getResource {
        resourceType
        text { status }
        url
        version
        name
        status
        experimental
        date
        publisher
        contact {
          name
          telecom {
             ...reference
          }
        }
        description
        jurisdiction {
          coding {
             ...reference
          }
        }
        copyright
        fhirVersion
        mapping { identity uri name }
        kind
        abstract
        type
        baseDefinition
        derivation
        snapshot {
          element {
            id
            path
            short
            definition
            comment
            alias
            min
            max
            base { path min max }
            constraint {
              key
              severity
              human
              expression
              xpath
              source
              extension {
                 ...extension
              }
            }
            mustSupport
            isModifier
            isModifierReason
            isSummary
            mapping {
              identity
              map
            }
            type {
              code
              targetProfile
            }
            binding {
              strength
              description
              valueSet
              extension {
                ...extension
              }
            }
            slicing {
              ...slicing
            }
        }
      }
      differential {
        element {
          id
          path
          short
          min
          max
          sliceName
          type { code targetProfile }
          patternCoding { ...reference }
          definition
          comment
          mustSupport
          slicing { ...slicing }
          fixedUri
          fixedCode
        }
       }
     }
   }

   fragment reference on Reference {
     system
     value
     code
   }

   fragment extension on Extension {
     url
     valueBoolean
     valueMarkdown
     valueCanonical
     valueString
   }

   fragment slicing on ElementSlicing {
     ordered
     rules
     description
     discriminator { type path }
   }

   "
    :expected (read-edn "deep-response.edn")}

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
   }"
    :expected (read-edn "basic.edn")}

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
    :vars {:ep "NEWHOPE"}
    :expected (read-edn "basic-vars.edn")}

   :errors
   ;; Test how long it take when a single deeply nested resolver resolves an error.
   ;; Also shows the cost of the call to distinct
   ;; This probably has gotten more expensive because the number of error maps has increased
   ;; due to the fix that put indexes into the path
   {:query "{ planets { name moons { name bases { name }}}}"
    :schema planets-schema
    :expected (read-edn "errors.edn")}})

(defmacro ^:private benchmark [expr]
  `(-> ~expr
       (c/quick-benchmark nil)
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
         variables :vars
         :keys [schema expected]
         :or {schema compiled-star-wars-schema}} (get benchmark-queries benchmark-name)
        parse-time (benchmark (parser/parse-query schema query-string))
        parsed-query (parser/parse-query schema query-string)
        actual-result (simplify (execute-parsed-query parsed-query variables nil))
        _ (do
            (when-not (= actual-result expected)
              (println "Benchmark returned unexpected result:\n\n")
              (pprint/pprint actual-result)
              (throw (IllegalStateException. "Benchmark did not return expected result.")))
            (println "Running benchmark" (name benchmark-name) "(exec) ..."))
        exec-time (benchmark (execute-parsed-query parsed-query variables nil))]
    [(name benchmark-name) parse-time exec-time]))

(defn ^:private test-benchmark
  [benchmark-name]
  (let [{query-string :query
         variables :vars
         :keys [schema expected]
         :or {schema compiled-star-wars-schema}} (get benchmark-queries benchmark-name)
        actual-result (simplify (execute schema query-string variables nil))]
    (when-not (= actual-result expected)
      (println "Benchmark returned unexpected result:\n\n")
      (pprint/pprint actual-result))))

(defn ^:private test-benchmarks
  []
  (doseq [benchmark-name (keys benchmark-queries)]
    (println "Testing" benchmark-name)
    (test-benchmark benchmark-name)))

(defn ^:private git-commit
  []
  (-> (sh "git" "rev-parse" "HEAD")
      :out
      (or "UNKNOWN")
      str/trim
      (subs 0 8)))

(def ^:private dataset-file "perf/benchmarks.csv")

(defn ^:private read-dataset
  []
  (let [[header-row & raw-data]
        (-> (io/file dataset-file)
            io/reader
            csv/read-csv)
        ;; read-csv reads all columns as strings, so we need to do some
        ;; work to get them back to numbers.
        parse-double (fn [row ix]
                       (update row ix #(Double/parseDouble %)))]
    (into [header-row]
          (->> raw-data
               (mapv #(parse-double % 3))
               (mapv #(parse-double % 4))))))

(defn ^:private run-benchmarks
  [options]
  (let [executor (ThreadPoolExecutor. 0 10 5 TimeUnit/SECONDS (SynchronousQueue.))]
    (try
      (test-benchmarks)
      (let [prefix [(format "%tY%<tm%<td" (Date.))
                    (or (:commit options) (git-commit))]
            new-benchmarks (->> (map run-benchmark (keys benchmark-queries))
                                (map #(into prefix %)))
            dataset (into (read-dataset) new-benchmarks)]

        (when-not (:no-print options)
          (println "\nTest                 Parse (ms)  Exec (ms)")
          (doseq [[_date _sha test-name parse-ms exec-ms] new-benchmarks]
            (println (format "%-20s    %7.3f    %7.3f" test-name parse-ms exec-ms)))
          (flush))

        (when-not (:no-store options)
          (with-open [w (-> dataset-file io/file io/writer)]
            (csv/write-csv w dataset))

          (println "Updated" dataset-file)))
      (finally
        (.shutdownNow executor)
        (shutdown-agents)))))

(def ^:private cli-opts
  [["-p" "--no-print" "Do not print the results of the test" :default false]
   ["-c" "--commit SHA" "Specify Git commit SHA; defaults to current commit (truncated to 8 chars)"]
   ["-s" "--no-store" "Do not save the results in perf/benchmarks.csv" :default false]
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


(defn ^:private selection-tree->field-tuples
  "Converts a selection tree, recursively, into lazy seq of field tuples.

  Each tuple is a field name the arguments to the field (usually nil)."
  [tree]
  (mapcat (fn [[field-name instances]]
            (let [fields (->> instances
                              (mapv #(vector field-name (:args %))))
                  sub-selections (->> instances
                                      (map :selections)
                                      (mapcat selection-tree->field-tuples))]
              (into fields sub-selections)))
          tree))


(comment

  (test-benchmarks)

  (test-benchmark :deep)
  ;; Some shims used when investigating the performance of the preview API:

  (let [parsed-query (parser/parse-query planets-schema introspection-query-raw)
        context (executor/parsed-query->context parsed-query)]
    (c/quick-bench
      (-> context
          executor/selections-seq
          doall))

    nil)


  )

