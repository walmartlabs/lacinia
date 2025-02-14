; Copyright (c) 2018-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.internal-utils-tests
  (:require
    [clojure.test :refer [deftest testing is]]
    [com.walmartlabs.lacinia.internal-utils :refer [assoc-in! update-in! deep-merge]]
    [clojure.string :as str]
    [flatland.ordered.map :refer [ordered-map]])
  (:import
    (clojure.lang ExceptionInfo)))


(def ^:private subject
  '{:objects
    {:Ebb
     {:fields {:name {:type String}}}
     :Flow
     {:fields {:id {:type String
                    :args
                    {:show {:type Boolean}}}}}}})

(deftest assoc-in!-test
  (is (= '{:description "Ebb Desc"
           :fields {:name {:type String}}}
         (-> subject
             (assoc-in! [:objects :Ebb :description] "Ebb Desc")
             (get-in [:objects :Ebb]))))

  (when-let [e (is (thrown-with-msg? ExceptionInfo #"Intermediate key not found during assoc-in!"
                                     (-> subject
                                         (assoc-in! [:objects :Flow :fields :missing :description] "this shall fail"))))]
    (is (= '{:key :missing
             :map {:id {:args {:show {:type Boolean}}
                        :type String}}
             :more-keys (:description)
             :value "this shall fail"}
           (ex-data e)))))

(deftest update-in!-test
  (is (= {:type "String"}
         (-> subject
             (update-in! [:objects :Ebb :fields :name :type] name)
             (get-in [:objects :Ebb :fields :name]))))
  (is (= {:show {:type "Boolean Type"}}
         (-> subject
             (update-in! [:objects :Flow :fields :id :args :show :type] str " Type")
             (get-in [:objects :Flow :fields :id :args]))))

  (when-let [e (is (thrown-with-msg? ExceptionInfo #"Intermediate key not found during update-in!"
                                     (update-in! subject [:objects :Ebb :fields :missing :description] str/upper-case)))]
    (is (= '{:key :missing
             :map {:name {:type String}}
             :more-keys (:description)}
           (ex-data e)))))

(deftest test-deep-merge
  (= (ordered-map [[:author :com.walmartlabs.lacinia.schema/null]])
     (deep-merge
       (ordered-map [[:author (ordered-map [[:name "John Doe"]])]])
       (ordered-map [[:author :com.walmartlabs.lacinia.schema/null]]))
     (deep-merge
       (ordered-map [[:author :com.walmartlabs.lacinia.schema/null]])
       (ordered-map [[:author (ordered-map [[:name "John Doe"]])]])))

  (= (ordered-map [[:author nil]])
     (deep-merge
       (ordered-map [[:author (ordered-map [[:name "John Doe"]])]])
       (ordered-map [[:author nil]]))
     (deep-merge
       (ordered-map [[:author nil]])
       (ordered-map [[:author (ordered-map [[:name "John Doe"]])]]))))
