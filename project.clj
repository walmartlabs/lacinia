(defproject com.walmartlabs/lacinia "0.26.0 "
  :description "A GraphQL server implementation in Clojure"
  :license {:name "Apache, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :plugins [[lein-codox "0.10.3"]
            [test2junit "1.2.5"]]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-antlr "0.2.4"]
                 [org.flatland/ordered "1.5.6"
                  :exclusions [org.clojure/tools.macro]]]
  :profiles {:dev {:dependencies [[criterium "0.4.4"]
                                  [expound "0.5.0"]
                                  [joda-time "2.9.9"]
                                  [org.clojure/test.check "0.9.0"]
                                  [org.clojure/data.csv "0.1.4"]
                                  [org.clojure/tools.cli "0.3.5"]
                                  [org.clojure/data.json "0.2.6"]]}}
  :aliases {"benchmarks" ["run" "-m" "perf"]}
  :jvm-opts ["-Xmx1g"]
  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit")
  :codox {:source-uri "https://github.com/walmartlabs/lacinia/blob/master/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}})
