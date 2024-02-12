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
  (:require [clojure.tools.build.api :as build]))

(def basis (delay (build/create-basis {:project "deps.edn"})))

(defn compile-java [& _]
  (build/delete {:path "target/classes"})
  (build/javac {:src-dirs ["src"]
                :class-dir "target/classes"
                :basis @basis}))