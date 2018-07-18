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

(ns com.walmartlabs.lacinia.nested-list-tests
  "Sanity check that nested lists work."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [simplify]]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia :as lacinia]))


(def verse [["eenie" "meenie"]
            ["minie" "moe"]
            ["catch" "a" "tiger"]
            ["by" "the" "toe"]])

(def compiled-schema
  (-> '{:queries
        {:verse_out {:type (non-null (list (non-null (list (non-null String)))))
                     :resolve :verse-out}
         :verse_echo {:type (list (list (non-null String)))
                      :args {:in {:type (list (list String))}}
                      :resolve :verse-echo}}}
      (assoc-in [:queries :verse_echo :args :in :default-value] verse)
      (util/attach-resolvers {:verse-out (constantly verse)
                              :verse-echo (fn [_ args _]
                                            (:in args))})
      schema/compile))

(defn execute
  ([q] (execute q nil))
  ([q vars]
   (-> (lacinia/execute compiled-schema q vars nil)
       simplify)))

(deftest can-resolve-2d-list
  (is (= {:data {:verse_out verse}}
         (execute "{ verse_out }"))))

(deftest nested-list-as-default-for-argument
  (is (= {:data {:verse_echo verse}}
         (execute "{ verse_echo }"))))

(deftest literal-nested-list
  (is (= {:data {:verse_echo [["tiger"
                               "tiger"]
                              ["burning"
                               "bright"]]}}
         (execute "{ verse_echo (in:
          [[\"tiger\", \"tiger\"]
           [\"burning\" \"bright\"]])
         }"))))

(deftest enforces-non-null-in-nested-lists
  (is (= {:data {:verse_echo [["oops"
                               nil
                               "fail!"]]}
          :errors [{:locations [{:column 2
                                 :line 1}]
                    :message "Non-nullable field was null."
                    :path [:verse_echo]
                    :extensions {:arguments {:in [["oops"
                                                   nil
                                                   "fail!"]]}}}]}
         (execute "{verse_echo (in:
         [[\"oops\", null, \"fail!\"  ]]
         )}"))))

(deftest input-from-variable
  (is (= {:data {:verse_echo [["humpty"
                               "dumpty"]
                              ["sat"
                               "on"
                               "a"
                               "wall"]]}}
         (execute "query ($verse: [[String]]) {
           verse_echo(in: $verse)
         }"
                  {:verse [["humpty" "dumpty"]
                           ["sat" "on" "a" "wall"]]}))))

(deftest input-from-variable-default
  (is (= {:data {:verse_echo [["twinkle"
                               "twinkle"]
                              ["little"
                               "star"]]}}
         (execute "query ($verse: [[String]] = [[\"twinkle\" \"twinkle\"]
          [\"little\" \"star\"]]) {
           verse_echo (in: $verse)
          } "))))

