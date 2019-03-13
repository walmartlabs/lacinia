; Copyright (c) 2019-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.scalar-tests
  (:require
    [clojure.test :refer :all]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :as utils]))

(def ^:private id-schema
  (schema/compile {:queries
                   {:convert {:type :ID
                              :args {:value {:type :ID}}
                              :resolve (fn [_ args _]
                                         (:value args))}}}))
(deftest numeric-id-parsed-to-string
  (is (= {:data {:convert "232"}}
         (utils/execute id-schema "{ convert(value: 232) }"))))

(deftest string-id-passed-through-unchanged
  (is (= {:data {:convert "r2d2"}}
         (utils/execute id-schema "{ convert(value: \"r2d2\") }"))))

(deftest non-numeric-id-is-failure
  (is (= {:errors [{:extensions {:argument :value
                                 :field :convert
                                 :type-name :ID
                                 :value 3.41}
                    :locations [{:column 3
                                 :line 1}]
                    :message "Exception applying arguments to field `convert': For argument `value', unable to convert 3.41 to scalar type `ID'."}]}
         (utils/execute id-schema "{ convert(value: 3.41) }"))))
