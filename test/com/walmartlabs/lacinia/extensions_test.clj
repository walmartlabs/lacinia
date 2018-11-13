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

(ns com.walmartlabs.lacinia.extensions-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :as r]
    [com.walmartlabs.test-utils :refer [compile-schema execute]]))

(def ^:private compiled-schema
  (compile-schema "extensions-schema.edn"
                  {:queries/extension (fn [_ _ _]
                                        (r/with-extensions "OK"
                                                           assoc-in [:foo :bar] :baz))
                   :queries/warning (fn [_ _ _]
                                      (r/with-warning "WARN"
                                                      {:message "Warning!"
                                                       :foo :bar}))}))

(deftest with-extensions
  (is (= {:data {:extension "OK"}
          :extensions {:foo {:bar :baz}}}
         (execute compiled-schema "{ extension }"))))

(deftest with-warning
  (is (= {:data {:warning "WARN"}
          :extensions {:warnings [{:extensions {:foo :bar}
                                   :locations [{:column 3
                                                :line 1}]
                                   :message "Warning!"
                                   :path [:warning]}]}}
         (execute compiled-schema "{ warning }"))))

(deftest combined
  (is (= {:data {:extension "OK"
                 :warning "WARN"}
          :extensions {:foo {:bar :baz}
                       :warnings [{:extensions {:foo :bar}
                                   :locations [{:column 3
                                                :line 1}]
                                   :message "Warning!"
                                   :path [:warning]}]}}
         (execute compiled-schema "{ warning extension }"))))
