(defproject com.walmartlabs/lacinia "0.36.0-alpha-2"
  :description "A GraphQL server implementation in Clojure"
  :url "https://github.com/walmartlabs/lacinia"
  :license {:name "Apache, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :plugins [[lein-codox "0.10.3"]
            [test2junit "1.2.5"]]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-antlr "0.2.5"]
                 [org.flatland/ordered "1.5.7"
                  :exclusions [org.clojure/tools.macro]]
                 [org.clojure/data.json "0.2.6"]]
  :source-paths ["src"
                 "vendor-src"]
  :profiles {:dev {:dependencies [[criterium "0.4.5"]
                                  [expound "0.7.2"]
                                  [joda-time "2.10.3"]
                                  [com.walmartlabs/test-reporting "0.1.0"]
                                  [io.aviso/logging "0.3.2"]
                                  [io.pedestal/pedestal.log "0.5.5"]
                                  [org.clojure/test.check "0.10.0"]
                                  [org.clojure/data.csv "0.1.4"]
                                  [org.clojure/tools.cli "0.4.2"]]}}
  :aliases {"benchmarks" ["run" "-m" "perf"]}
  :jvm-opts ["-Xmx1g" "-XX:-OmitStackTraceInFastThrow"]
  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit")
  :codox {:source-uri "https://github.com/walmartlabs/lacinia/blob/master/{filepath}#L{line}"
          :source-paths ["src"]                             ; and not vendor-src
          :metadata   {:doc/format :markdown}})
