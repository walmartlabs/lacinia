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

(ns com.walmartlabs.lacinia.mutable-context-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.test-utils :refer [compile-schema execute]]))

(def ^:private schema (compile-schema "mutable-context-schema.edn"
                                      {:root (fn [_ args _]
                                               (cond-> {:container {:id "0001"
                                                                    :leaf "DEFAULT"}}
                                                 (:trigger args) (resolve/with-context {::leaf-value "OVERRIDE"})))
                                       :leaf (fn [context _ container]
                                               (or (::leaf-value context)
                                                   (:leaf container)))}))

(deftest resolver-may-modify-nested-context
  (is (= {:data {:disabled {:container {:id "0001"
                                        :leaf "DEFAULT"}}
                 :enabled {:container {:id "0001"
                                       :leaf "OVERRIDE"}}}}
         (execute schema
                  "{ enabled: root(trigger: true) { container { id leaf }}
                     disabled: root { container { id leaf }}}"))))
