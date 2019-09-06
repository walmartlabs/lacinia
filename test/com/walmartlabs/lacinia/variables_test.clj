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

(ns com.walmartlabs.lacinia.variables-test
  (:require
   [clojure.test :refer [deftest is are testing]]
   [com.walmartlabs.test-schema :refer [test-schema]]
   [com.walmartlabs.test-utils :refer [compile-schema execute]]
   [com.walmartlabs.lacinia :refer [execute-parsed-query]]
   [com.walmartlabs.lacinia.schema :as schema]
   [com.walmartlabs.lacinia.parser :as parser]))

(def compiled-schema (schema/compile test-schema))

(deftest variables-can-have-default-values
  (let [q (parser/parse-query compiled-schema
                              "query ($id : String =  \"2001\") {

                                 droid (id : $id) { name }

                               }")]
    (is (= {:data {:droid {:name "R2-D2"}}}
           (execute-parsed-query q nil nil)))
    (is (= {:data {:droid {:name "C-3PO"}}}
           (execute-parsed-query q {:id "2000"} nil)))))

(deftest references-missing-variable
  (let [compiled-schema (schema/compile {:queries {:echo {:type :String
                                                          :args {:input {:type '(non-null String)}}
                                                          :resolve (fn [_ {:keys [input]} _]
                                                                     (when-not input
                                                                       (throw (NullPointerException.)))

                                                                     input)}}})
        q (parser/parse-query compiled-schema
                              "query ($i : String) {
                                 echo (input : $i)
                               }")]

    ;; Double check the success case:

    (is (= {:data {:echo "foo"}}
             (execute-parsed-query q {:i "foo"} nil)))

    ;; Should not get as far as the resolver function:

    (is (= {:errors
            [{:message "No variable `i' was supplied for argument `__Queries/echo.input', which is required."
              :locations [{:line 2
                           :column 34}]
              :extensions
              {:argument :__Queries/echo.input
               :field :__Queries/echo
               :variable-name :i}}]}
           (execute-parsed-query q nil nil)))))

(deftest fragments-can-reference-variables
  (let [q (parser/parse-query compiled-schema
                              "
   query ($terse : Boolean = true, $id : String) {
     droid (id: $id) {
       ... droidInfo
     }
   }

   fragment droidInfo on droid {
     name
     best_friend @skip(if: $terse) {
       name
     }
   }")]
    (is (= {:data {:droid {:name "R2-D2"}}}
           (execute-parsed-query q nil nil))
        "should skip best_friend when variable defaults to true")
    (is (= {:data {:droid {:name "C-3PO"
                           :best_friend {:name "Luke Skywalker"}}}}
           (execute-parsed-query q {:terse false
                                    :id "2000"} nil))
        "should not skip best_friend when variable is set to false")
    (is (= {:data {:droid {:name "R2-D2"}}}
           (execute-parsed-query q {:terse true} nil))
        "should skip best_friend when variable is set to true")))

(defn with-tag
  [v]
  (if-let [type (::type v)]
    (schema/tag-with-type v type)
    v))

(deftest variables-with-missing-value
  (let [villains {"01" {:name "Wilhuff Tarkin" ::type :villain}
                  "02" {:name "Darth Vader" ::type :villain}}
        get-villain (fn [episode]
                      (let [id (condp = episode
                                 "NEW HOPE" "01"
                                 "EMPIRE" "02"
                                 "JEDI" "02"
                                 nil)]
                        (get villains id)))
        schema {:interfaces {:character {:fields {:id {:type 'String}
                                                  :name {:type 'String}}}}
                :objects {:villain {:implements [:character]
                                    :fields {:id {:type 'String}
                                             :name {:type 'String}}}}
                :mutations {:changeName {:type :character
                                         :args {:id {:type 'String}
                                                :new_name {:type 'String}}
                                         :resolve (fn [ctx args v]
                                                    (let [{:keys [id new_name]} args]
                                                      (let [new-name (if (contains? args :new_name)
                                                                       new_name
                                                                       "Darth Bane")]
                                                        (-> (get villains id)
                                                            (assoc :name new-name)
                                                            (with-tag)))))}}
                :queries {:villain {:type :villain
                                    :args {:episode {:type 'String}}
                                    :resolve (fn [ctx args v]
                                               (get-villain (:episode args)))}}}
        compiled-schema (schema/compile schema)]

    (testing "query with a variable without default-value"
      (let [q (parser/parse-query compiled-schema
                                  "query ($episode : String) {
                                      villain (episode : $episode) {
                                         name
                                      }
                                  }")]
        (is (= {:data {:villain nil}}
               (execute-parsed-query q nil nil))
            "should return no data when variable is missing")
        (is (= {:data {:villain nil}}
               (execute-parsed-query q {:episode nil} nil))
            "should return no data when variable is null")
        (is (= {:data {:villain {:name "Wilhuff Tarkin"}}}
               (execute-parsed-query q {:episode "NEW HOPE"} nil))
            "should return a villain")))

    (testing "query with a variable with a default value"
      (let [q (parser/parse-query compiled-schema
                                  "query ($episode : String = \"EMPIRE\") {
                                      villain (episode : $episode) {
                                         name
                                      }
                                  }")]
        (is (= {:data {:villain {:name "Darth Vader"}}}
               (execute-parsed-query q nil nil))
            "should return default value when variable is not provided")

        (is (= {:data {:villain {:name "Wilhuff Tarkin"}}}
               (execute-parsed-query q {:episode "NEW HOPE"} nil))
            "should return a villain")))

    (testing "mutation with a variable without a default value"
      (let [q (parser/parse-query compiled-schema
                                  "mutation ($id : String!, $name : String) {
                                     changeName(id: $id, new_name: $name) {
                                        name
                                     }
                                   }")]
        (is (= {:data {:changeName {:name "Rey"}}}
               (execute-parsed-query q {:id "01" :name "Rey"} nil)))
        (is (= {:data {:changeName {:name nil}}}
               (execute-parsed-query q {:id "02" :name nil} nil))
            "should change name to null")
        (is (= {:data {:changeName {:name "Darth Bane"}}}
               (execute-parsed-query q {:id "01"} nil))
            "should return Darth Bane when new_name is not present in arguments")))

    (testing "mutation with a variable with a NULL default value"
      (let [q (parser/parse-query compiled-schema
                                  "mutation ($id : String!, $name : String = null) {
                                     changeName(id: $id, new_name: $name) {
                                        name
                                     }
                                   }")]
        (is (= {:data {:changeName {:name "Rey"}}}
               (execute-parsed-query q {:id "01" :name "Rey"} nil)))
        (is (= {:data {:changeName {:name nil}}}
               (execute-parsed-query q {:id "02" :name nil} nil))
            "should change name to null when variable is null")
        (is (= {:data {:changeName {:name nil}}}
               (execute-parsed-query q {:id "01"} nil))
            "should use default-value that is null (as opposed to returning Darth Bane)")))))
