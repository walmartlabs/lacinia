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

(ns com.walmartlabs.lacinia.fragments-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia :refer [execute]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :as utils]))

(defn ^:private resolve-characters
  [_ _ _]
  [(schema/tag-with-type {:name "R2-D2" :power "AC"} :droid)
   (schema/tag-with-type {:name "Luke" :home_world "Tatooine"} :human)])

(def ^:private schema
  (utils/compile-schema "fragments-schema.edn"
                        {:resolve-characters resolve-characters}))

(defn ^:private q [query]
  (utils/simplify (execute schema query nil nil)))

(deftest inline-fragments
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"}
                              {:name "Luke"
                               :home_world "Tatooine"}]}}
         (q "{ characters {
           name
           ... on droid { power }
           ... on human { home_world }
           }
           }"))))

(deftest query-root-fragment
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"}
                              {:name "Luke"
                               :home_world "Tatooine"}]}}
         (q "{ ... on QueryRoot {
           characters {
           name
           ... on droid { power }
           ... on human { home_world }
           }
           }
           }"))))

(deftest named-fragments
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"}
                              {:home_world "Tatooine"
                               :name "Luke"}]}}

         (q "query {

           characters {

             name

             ... droidFragment
             ... humanFragment
           }
         }

         fragment droidFragment on droid { power }
         fragment humanFragment on human { home_world }

         "))))

(deftest nested-fragments
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"}
                              {:home_world "Tatooine"
                               :name "Luke"}]}}

         (q "query {

           characters { ... characterFragment }

         }

         fragment characterFragment on character {

             name

             ... droidFragment
             ... humanFragment

         }

         fragment droidFragment on droid { power }
         fragment humanFragment on human { home_world }

         "))))
