; Copyright (c) 2018-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.object-scalars-test
  "Tests for scalars that consume and produce a map of un-typed data."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.util :refer [attach-resolvers attach-scalar-transformers]]
    [com.walmartlabs.test-utils :refer [execute]]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.schema :as schema]))


(def ^:private compiled-schema
  (-> (io/resource "object-scalars-schema.edn")
      slurp
      edn/read-string
      (attach-scalar-transformers {:scalars/data.parse identity
                                   :scalars/data.serialize identity})
      (attach-resolvers {:queries/echo (fn [_ {:keys [input]} _]
                                         {:input_arg input})})
      schema/compile))

(defn ^:private execute-var
  [v]
  (execute compiled-schema "query ($v : Data) { echo (input: $v) }"
           {:v v}
           nil))

(deftest numeric
  (is (= {:data
          {:echo
           {:input_arg 2}}}
         (execute compiled-schema "{ echo(input: 2) }"))))

(deftest numeric-var
  (is (= {:data {:echo {:input_arg 1138}}}
         (execute-var 1138))))

(deftest string
  (is (= {:data {:echo {:input_arg "zardoz"}}}
         (execute compiled-schema "{ echo (input: \"zardoz\" ) }"))))

(deftest simple-map
  (is (= {:data
          {:echo
           {:input_arg {:max 20
                        :order "desc"
                        :skip 10}}}}
         (execute compiled-schema "{ echo(input: {skip: 10, max: 20, order: \"desc\"}) }"))))

(deftest simple-map-var
  (is (= {:data
          {:echo
           {:input_arg
            {:max 20
             :order "asc"
             :skip 120}}}}
         (execute-var {:skip 120
                       :max 20
                       :order "asc"}))))

(deftest var-nested-in-map-not-allowed
  (is (= {:errors [{:extensions {:argument :Query/echo.input
                                 :field-name :Query/echo
                                 :variable-name :input}
                    :locations [{:column 21
                                 :line 1}]
                    :message "Exception applying arguments to field `echo': Argument `input' contains a scalar argument with nested variables, which is not allowed."}]}
         (execute compiled-schema
                  "query ($p : Data) { echo (input: {p: $p}) }"
                  {:p {:order 97
                       :date "2018115"}}
                  nil))))

(deftest array-property
  (is (= {:data
          {:echo
           {:input_arg {:vals [1
                               3
                               true
                               3.14]}}}}
         (execute compiled-schema "{ echo(input: {vals: [1 3 true 3.14]})}"))))

(deftest nested-map-property
  (is (= {:data
          {:echo
           {:input_arg
            {:name "fred"
             :address {:street "100 Bedrock Lane."}}}}}
         (execute compiled-schema "{ echo(input: {name: \"fred\" address: {street: \"100 Bedrock Lane.\"}}) }"))))
