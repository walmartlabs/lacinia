(defproject com.walmartlabs/lacinia "0.13.0"
  :description "A GraphQL server implementation in Clojure"
  :license {:name "Apache, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :plugins [[lein-codox "0.10.2"]]
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clojure-future-spec "1.9.0-alpha14"]
                 [clj-antlr "0.2.4"]
                 [org.flatland/ordered "1.5.4"]]
  :profiles {:dev {:dependencies [[criterium "0.4.4"]
                                  [org.clojure/data.json "0.2.6"]]}}
  :codox {:source-uri "https://github.com/walmartlabs/lacinia/blob/master/{filepath}#L{line}"
          :metadata   {:doc/format :markdown}})
