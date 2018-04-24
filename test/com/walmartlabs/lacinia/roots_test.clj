; Copyright (c) 2017-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns com.walmartlabs.lacinia.roots-test
  "Tests related to specifying  operation root object names."
  (:require
    com.walmartlabs.lacinia.parser
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :refer [execute compile-schema]]))

(deftest default-root-name
  (let [schema (schema/compile {})]
    (is (= {:data {:__schema {:queryType {:name "QueryRoot"}}}}
           (execute schema "{__schema { queryType { name } } }")))))

(deftest can-specify-root-name
  (let [schema (schema/compile {:roots {:query :MyQueries}})]
    (is (= {:data {:__schema {:queryType {:name "MyQueries"}}}}
           (execute schema "{__schema { queryType { name } } }")))))

(deftest root-object-may-contain-fields
  (let [schema (compile-schema "root-object-schema.edn"
                               {:queries/fred (constantly "Flintstone")
                                :Barney/last-name (constantly "Rubble")})]
    (is (= {:data {:barney "Rubble"
                   :fred "Flintstone"}}
           (execute schema "{
             fred
             barney: last_name
           }")
           ))))

(deftest name-collisions-are-detected
  (try
    (compile-schema "root-object-with-conflicts-schema.edn"
                    {:queries/fred (constantly "Flintstone")
                     :Barney/last-name (constantly "Rubble")})
    (is false "should be unreachable")
    (catch Throwable e
      (is (= "Name collision compiling schema. Query `__Queries/last_name' conflicts with `Barney/last_name'."
             (.getMessage e)))
      (is (= {:field-name :last_name
              :operation :query}
             (ex-data e))))))

(deftest root-is-a-union
  (let [schema (compile-schema "union-query-root-schema.edn"
                               {:queries/version (constantly "1.0")
                                :Fred/family-name (constantly "Flintstone")
                                :Barney/last-name (constantly "Rubble")})]

    ;; Member objects' fields are merged into __Queries along with extras.

    (is (= {:data {:__schema {:queryType {:name "__Queries"}}}}
           (execute schema "{__schema { queryType { name } } }")))

    (is (= ["family_name"
            "last_name"
            "version"]
           (->> (execute schema "{__type(name: \"__Queries\") { fields { name }}}")
                :data :__type :fields
                (mapv :name))))

    (is (= {:data {:barney "Rubble"
                   :fred "Flintstone"
                   :version "1.0"}}
           (execute schema
                    "{ version
                       fred: family_name
                       barney: last_name
                    }")))))
