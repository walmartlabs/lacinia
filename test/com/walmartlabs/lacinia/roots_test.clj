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
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :refer [execute compile-schema]]))

(deftest default-root-name
  (let [schema (schema/compile {})]
    (is (= {:data {:__schema {:queryType {:name "Query"}}}}
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
  (let [e (is (thrown? Exception
                       (compile-schema "root-object-with-conflicts-schema.edn"
                                       {:queries/fred (constantly "Flintstone")
                                        :Barney/last-name (constantly "Rubble")})))]
    (is (= "Name collision compiling schema: `Barney/last_name' already exists with value from :queries."
           (.getMessage e)))
    (is (= {:field-name :last_name}
           (ex-data e)))))

