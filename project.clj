(defproject com.walmartlabs/lacinia "0.31.0-SNAPSHOT"
  :description "A GraphQL server implementation in Clojure"
  :license {:name "Apache, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :plugins [[lein-codox "0.10.3"]
            [test2junit "1.2.5"]]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-antlr "0.2.4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.flatland/ordered "1.5.6"
                  :exclusions [org.clojure/tools.macro]]]
  :profiles {:dev {:dependencies [[criterium "0.4.4"]
                                  [expound "0.7.1"]
                                  [joda-time "2.10"]
                                  [com.walmartlabs/test-reporting "0.1.0"]
                                  [io.aviso/logging "0.3.1"]
                                  [io.pedestal/pedestal.log "0.5.4"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/data.csv "0.1.4"]
                                  [org.clojure/tools.cli "0.3.7"]]}}
  :aliases {"benchmarks" ["run" "-m" "perf"]}
  :jvm-opts ["-Xmx1g" "-XX:-OmitStackTraceInFastThrow"]
  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit")
  :codox {:source-uri "https://github.com/walmartlabs/lacinia/blob/master/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}})
