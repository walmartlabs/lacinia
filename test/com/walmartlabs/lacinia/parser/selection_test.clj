; Copyright (c) 2020-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.parser.selection-test
  "Tests related to the executor/selection function and related data, introduced in 0.38.0."
  (:require
    [clojure.test :refer [is deftest use-fixtures]]
    [com.walmartlabs.lacinia.executor :as executor]
    [com.walmartlabs.lacinia.protocols :as p]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :refer [compile-sdl-schema execute]]))

(def ^:private *log (atom []))

(defn ^:private log
  [& kvs]
  (let [pairs (partition-all 2 kvs)]
    (swap! *log into pairs)))

(defn ^:private reset-log
  [f]
  (reset! *log [])
  (f))

(use-fixtures :each reset-log)

(deftest access-to-selection
  (let [f (fn [context _ _]
            (let [s (executor/selection context)
                  directives (p/directives s)]
              (log :selection {:field-selection? (p/field-selection? s)
                               :qualified-name (p/qualified-name s)
                               :field-name (p/field-name s)
                               :alias (p/alias-name s)}
                   :directive-keys (-> directives keys sort)
                   :directive-names (->> directives
                                         :concise
                                         (map p/directive-type))))
            "Done")
        schema (compile-sdl-schema "selection/simple.sdl"
                                   {:Query/basic f})
        result (execute schema "{ basic @concise }")]
    (is (= {:data {:basic "Done"}}
           result))
    (is (= '[[:selection {:field-selection? true
                          :qualified-name :Query/basic
                          :field-name :basic
                          :alias :basic}]
             [:directive-keys [:concise]]
             [:directive-names [:concise]]]
           @*log))))
