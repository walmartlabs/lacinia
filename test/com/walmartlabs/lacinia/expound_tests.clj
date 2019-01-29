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

(ns com.walmartlabs.lacinia.expound-tests
  "Tests that useful Expound messages are emitted for spec failures."
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.walmartlabs.test-reporting :refer [reporting]]
    com.walmartlabs.lacinia.expound
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.parser.schema :as ps]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))

(defmacro expect
  [spec value & substrings]
  `(let [explain# (with-out-str
                    (s/explain ~spec ~value))]
     (reporting {:explain explain#}
       (doseq [s# ~(vec substrings)]
         (is (str/includes? explain# s#))))))

(deftest uses-messages
  (expect ::schema/resolve nil
          "fn?"
          "implement the com.walmartlabs.lacinia.resolve/FieldResolver protocol"))

(deftest correctly-reports-incorrect-type-modifier
  (expect ::schema/field {:type '(something :String)}
          "type wrappers should be either (list type) or (non-null type)"))

(deftest can-report-enum-value
  (expect ::schema/enum-value 123 "string?" "simple-symbol?" "simple-keyword?")
  (expect ::schema/enum-value "this-and-that" "must be a valid GraphQL identifier"))

(deftest sdl-function-map
  (expect ::ps/fn-map {:foo :bar :gnip {:q 'gnop}}
          "fn?" "simple-keyword?"))

;; Really want a good message for this incompatible change in 0.31.0

(deftest parse-and-serialize-must-be-bare-functions
  (expect ::schema/parse (schema/as-conformer identity)
          "scalar parse and serialize functions must now be simple functions"
          "not clojure.spec conformers"
          "see release notes"))
