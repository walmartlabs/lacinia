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

(ns com.walmartlabs.lacinia.expound
  "Adds improved spec messages to Lacinia specs, when using [Expound](https://github.com/bhb/expound).

  This namespace should simply be required; it exports no functions or constants.

  Expound is an optional library for Lacinia and must be added to your project explicitly."
  {:added "0.26.0"}
  (:require
    [expound.alpha :refer [defmsg]]
    [com.walmartlabs.lacinia.schema :as schema]))

(defmsg ::schema/resolver-type "implement the com.walmartlabs.lacinia.resolve/FieldResolver protocol")

(defmsg ::schema/wrapped-type-modifier "type wrappers should be either (list type) or (non-null type)")

(defmsg ::schema/graphql-identifier "must be a valid GraphQL identifier: contain only letters, numbers, and underscores")

(defmsg ::schema/not-a-conformer "scalar parse and serialize functions must now be simple functions, not clojure.spec conformers (see release notes)")
