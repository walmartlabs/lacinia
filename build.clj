; Copyright (c) 2021-present Walmart, Inc.
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

;; clj -T:build <var>

(ns build
  (:require [clojure.tools.build.api :as b]
            [net.lewisship.build :refer [requiring-invoke]]))

(def lib 'com.walmartlabs/lacinia)
(def version "1.1")

(def jar-params {:project-name lib
                 :version version})

(defn clean
  [_params]
  (b/delete {:path "target"}))

(defn jar
  [_params]
  (requiring-invoke net.lewisship.build.jar/create-jar jar-params))

(defn deploy
  [_params]
  (clean nil)
  (jar nil)
  (requiring-invoke net.lewisship.build.jar/deploy-jar jar-params))

(defn codox
  [_params]
  (requiring-invoke net.lewisship.build.codox/generate
   {:project-name lib
    :version version
    :aliases [:dev]}))
