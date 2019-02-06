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

(ns com.walmartlabs.lacinia.validator
  "Implements query validation (eg. typechecking of vars, fragment types, etc.),
  but also a place where complexity analysis will occur."
  {:no-doc true}
  (:require [com.walmartlabs.lacinia.validation.scalar-leafs :refer [scalar-leafs]]
            [com.walmartlabs.lacinia.validation.fragment-names :refer [known-fragment-names]]
            [com.walmartlabs.lacinia.validation.no-unused-fragments
             :refer [no-unused-fragments]]))


;;-------------------------------------------------------------------------------
;; ## Rules

(def ^:private default-rules
  [scalar-leafs

   ;; fragments
   known-fragment-names
   no-unused-fragments])

;; —————————————————————————————————————————————————————————————————————————————
;; ## Public API

(defn validate
  "Performs validation of the parsed and prepared query against
  a set of default rules.

  Returns an sequence of error maps, which will be empty if there are no errors."
  [prepared-query]
  (mapcat #(% prepared-query) default-rules))
