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

(ns com.walmartlabs.lacinia.typename-test
  "Tests for the __typename metadata field on objects."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia :refer [execute]]
    [com.walmartlabs.test-utils :refer [simplify compile-schema]]))

(defn ^:private get-man
  [_ _ _]
  {:name "Adam"})

(defn ^:private get-machine
  [_ _ _]
  {:serial "X01"})

(defn ^:private get-all
  [_ _ _]
  [(schema/tag-with-type (get-man nil nil nil) :man)
   (schema/tag-with-type (get-machine nil nil nil) :machine)])

(def ^:private compiled-schema
  (compile-schema "typename-schema.edn"
                   {:get-man get-man
                   :get-machine get-machine
                   :get-all get-all}))

(defn ^:private q
  [query]
  (simplify
    (execute compiled-schema query nil nil)))

(deftest type-name-on-static-type
  ;; The :get_man query has a known concrete type, so
  ;; the type tag is supplied by Lacinia.
  (is (= {:data
          {:get_man
           {:__typename :man
            :name "Adam"}}}
         (q "{ get_man { __typename name }}"))))

(deftest type-name-on-dynamic-types
  (is (= {:data {:get_all [{:__typename :man
                            :name "Adam"}
                           {:__typename :machine
                            :serial "X01"}]}}
         (q "{ get_all { __typename
         ... on man { name }
         ... on machine { serial }
         }
       }"))))
