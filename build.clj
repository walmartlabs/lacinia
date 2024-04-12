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
  (:require [clojure.string :as string]
            [clojure.tools.build.api :as build]
            [net.lewisship.build :as b]))

(def lib 'com.walmartlabs/lacinia)
(def version (-> "VERSION.txt" slurp string/trim))
(def class-dir "target/classes")

(def jar-params {:project-name lib
                 :version version
                 :class-dir class-dir})

(defn clean
  [_params]
  (build/delete {:path "target"}))

(defn compile-java [_]
  (build/javac {:src-dirs ["java"]
                :class-dir class-dir
                :basis (build/create-basis)
                :javac-opts ["--release" "11"]}))

(defn jar
  [_params]
  (compile-java nil)
  (b/create-jar jar-params))

(defn deploy
  [_params]
  (clean nil)
  (b/deploy-jar (jar nil)))

(defn codox
  [_params]
  (b/generate-codox {:project-name lib
                     :version version
                     :aliases [:dev]}))

(def publish-dir "../apidocs/lacinia")

(defn publish
  "Generate Codox documentation and publish via a GitHub push."
  [_params]
  (println "Generating Codox documentation")
  (codox nil)
  (println "Copying documentation to" publish-dir "...")
  (build/copy-dir {:target-dir publish-dir
                   :src-dirs ["target/doc"]})
  (println "Committing changes ...")
  (build/process {:dir publish-dir
                  :command-args ["git" "commit" "-a" "-m" (str "lacinia " version)]})
  (println "Pushing changes ...")
  (build/process {:dir publish-dir
              :command-args ["git" "push"]}))
