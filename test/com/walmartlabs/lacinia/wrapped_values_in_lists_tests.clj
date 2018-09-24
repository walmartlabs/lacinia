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

(ns com.walmartlabs.lacinia.wrapped-values-in-lists-tests
  "Tests that wrapped values inside lists (returned by field resolvers) are properly handled."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :refer [with-context]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :as utils]))



(def ^:private compiled-schema
  (schema/compile
    {:objects
     {:IdObj
      {:fields
       {:id {:type 'String}
        :xid {:type 'String
              :resolve (fn [context _ obj]
                         (str (::root-prefix context) "-"
                              (::item-prefix context) "-"
                              (:id obj)))}}}}

     :queries
     {:objects
      {:type '(list :IdObj)
       :args {:root_prefix {:type 'String}}
       :resolve (fn [_ args _]
                  (with-context
                    (->> [100 200 555]
                         (map #(hash-map :id (format "%05d" %)))
                         (map-indexed (fn [i v]
                                        (with-context v
                                                      {::item-prefix (str "i" i)}))))
                    {::root-prefix (:root_prefix args)}))}}}))


(deftest exposeses-context-to-sub-resolvers
  (is (= {:data {:objects [{:id "00100"
                            :xid "r-i0-00100"}
                           {:id "00200"
                            :xid "r-i1-00200"}
                           {:id "00555"
                            :xid "r-i2-00555"}]}}
         (utils/execute compiled-schema
                        "{ objects(root_prefix: \"r\") { id xid }}"))))
