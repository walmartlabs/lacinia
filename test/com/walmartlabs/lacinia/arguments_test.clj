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

(ns com.walmartlabs.lacinia.arguments-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [compile-schema expect-exception execute]]
    [com.walmartlabs.lacinia.schema :as schema]))

(deftest reports-unknown-argument-type
  (expect-exception
    "Argument `__Queries/example.id' references unknown type `UUID'."
    {:arg-name :__Queries/example.id
     :schema-types {:scalar [:Boolean :Float :ID :Int :String]
                    :object [:MutationRoot :QueryRoot :SubscriptionRoot]}}
    (compile-schema "unknown-argument-type-schema.edn"
                    {:example identity})))

(def echo-schema (schema/compile {:queries
                                  {:echo
                                   {:type :String
                                    :args {:input {:type :String
                                                   :default-value "Excelsior!"}}
                                    :resolve (fn [_ {:keys [input]} _] input)}}}))

(deftest explicit-argument-overrides-default
  (is (= {:data {:echo "Shazam!"}}
         (execute echo-schema "{ echo(input: \"Shazam!\") }")))

  (is (= {:data {:echo "Bravo!"}}
         (execute echo-schema "query($s: String) { echo(input: $s) }" {:s "Bravo!"} nil))))

(deftest variable-default-used-when-arg-omitted
  (is (= {:data {:echo "Spoon!"}}
         (execute echo-schema "query($s: String = \"Whazzup!\") { echo(input: $s) }" {:s "Spoon!"} nil)))

  (is (= {:data {:echo "Whazzup!"}}
         (execute echo-schema "query($s: String = \"Whazzup!\") { echo(input: $s) }"))))

(deftest omitted-argument-uses-default
  (is (= {:data {:echo "Excelsior!"}}
         (execute echo-schema "{ echo }"))))

(deftest explicit-null-is-passed-through-even-when-default-is-present
  (is (= {:data {:echo nil}}
         (execute echo-schema "{ echo(input: null) }")))

  (is (= {:data {:echo nil}}
         (execute echo-schema "query($s : String) { echo(input: $s) }" {:s nil} nil))))

(deftest argument-is-omitted-if-no-default
  (let [schema (schema/compile {:queries
                                {:contains
                                 {:type :Boolean
                                  :args {:input {:type :String}}
                                  :resolve (fn [_ args _]
                                             (contains? args :input))}}})]
    (is (= {:data {:contains true}}
           (execute schema "{ contains(input: \"whatever\") }")))

    (is (= {:data {:contains true}}
           (execute schema "{ contains(input: null) }")))

    (is (= {:data {:contains false}}
           (execute schema "{ contains }")))

    ;; Providing an actual nil is passed through, as with a null literal
    (is (= {:data {:contains true}}
           (execute schema "query($s: String) { contains(input: $s) }" {:s nil} nil)))

    ;; Not providing the variable ends up the same as not providing any value at all
    ;; (the argument is omitted)
    (is (= {:data {:contains false}}
           (execute schema "query($s: String) { contains(input: $s) }" nil nil)))))

(deftest default-value-is-used-for-non-null-argument-when-not-provided
  (let [schema (schema/compile {:queries
                                {:echo
                                 {:type :String
                                  :args {:input {:type '(non-null String)
                                                 :default-value "Default"}}
                                  :resolve (fn [_ args _]
                                             (str "Echo: " (:input args)))}}})]
    (is (= {:data {:echo "Echo: whatever"}}
           (execute schema "{ echo(input: \"whatever\") }")))

    (is (= {:data {:echo "Echo: Default"}}
           (execute schema "{ echo }")))))
