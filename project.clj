(defproject com.walmartlabs/lacinia "0.39-alpha-7"
  :description "A GraphQL server implementation in Clojure"
  :url "https://github.com/walmartlabs/lacinia"
  :license {:name "Apache, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :plugins [[lein-codox "0.10.7"]
            [lein-cloverage "1.2.2"]
            [test2junit "1.2.5"]]
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [clj-antlr "0.2.9"]
                 [org.flatland/ordered "1.5.9"]
                 [org.clojure/data.json "2.4.0"]]
  :source-paths ["src"]
  :profiles {:dev {:dependencies [[criterium "0.4.6"]
                                  [org.clojure/core.async "1.3.618"]
                                  [expound "0.8.9"]
                                  [joda-time "2.10.10"]
                                  [com.walmartlabs/test-reporting "1.1"]
                                  [io.aviso/logging "1.0"]
                                  [io.pedestal/pedestal.log "0.5.8"]
                                  [org.clojure/test.check "1.1.0"]
                                  [org.clojure/data.csv "1.0.0"]
                                  [org.clojure/tools.cli "1.0.206"]]}}
  :aliases {"benchmarks" ["run" "-m" "perf"]}
  :jvm-opts ["-Xmx1g" "-XX:-OmitStackTraceInFastThrow"]
  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit")
  :codox {:source-uri "https://github.com/walmartlabs/lacinia/blob/master/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}})
