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

(ns com.walmartlabs.lacinia.parser.docs-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.parser.docs :refer [parse-docs]]
    [clojure.java.io :as io]))

(defn ^:private parse
  [path]
  (-> path
      io/resource
      slurp
      parse-docs))

(deftest basic-parse
  (is (= {:Character "A person who appears in one of the movies.

Persons are more than humans, and may include droids and computers."
          :Character/name "The primary name of the character.

For droids, this is the announced name, such as \"AreToo\"."}
         (parse "basic-docs.md"))))

(deftest empty-parse
  (is (= {}
         (parse-docs ""))))

(deftest ignores-before-first-header
  (is (= {:Customer "Someone we sell to."}
         (parse "preamble-docs.md"))))
