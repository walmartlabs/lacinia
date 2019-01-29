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

(ns com.walmartlabs.lacinia.arguments-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [compile-schema expect-exception]]))

(deftest reports-unknown-argument-type
  (expect-exception
    "Argument `__Queries/example.id' references unknown type `UUID'."
    {:arg-name :__Queries/example.id
     :schema-types {:scalar [:Boolean :Float :ID :Int :String]
                    :object [:MutationRoot :QueryRoot :SubscriptionRoot]}}
    (compile-schema "unknown-argument-type-schema.edn"
                    {:example identity})))
