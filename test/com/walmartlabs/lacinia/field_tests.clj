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

(ns com.walmartlabs.lacinia.field-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [expect-exception]]
    [com.walmartlabs.lacinia.schema :as schema]))

(deftest field-references-unknown-type
  (expect-exception
    "Field `Insect/legs' references unknown type `Six'."
    {:field-name :Insect/legs
     :schema-types {:object [:Insect
                             :Mutation
                             :Query
                             :Subscription]
                    :scalar [:Boolean
                             :Float
                             :ID
                             :Int
                             :String]}}
    (schema/compile {:objects
                     {:Insect
                      {:fields
                       {:legs {:type :Six}}}}})))
