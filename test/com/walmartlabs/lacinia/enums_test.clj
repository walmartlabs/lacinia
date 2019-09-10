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

(ns com.walmartlabs.lacinia.enums-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia :refer [execute]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-reporting :refer [reporting]]
    [com.walmartlabs.test-utils :as utils :refer [expect-exception]])
  (:import
    (java.util Date)))

(def compiled-schema (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver}))

(defn q
  ([query]
    (q query nil))
  ([query vars]
   (utils/simplify (execute compiled-schema query vars nil))))

(deftest returns-enums-as-keywords
  (is (= {:data {:hero {:appears_in [:NEWHOPE
                                     :EMPIRE
                                     :JEDI]
                        :name "R2-D2"}}}
         (q "{ hero { name appears_in }}"))))

(deftest can-provide-enum-as-bare-name
  (let [result (q "{ hero(episode: NEWHOPE) { name }}")
        hero-name (-> result :data :hero :name)]
    (reporting result
      (is (= "Luke Skywalker" hero-name)))))

(deftest handling-of-invalid-enum-value
  (let [result (q "{ hero (episode: CLONES) { name }}")
        errors (-> result :errors)
        first-error (first errors)]
    (is (-> result (contains? :data) not))
    (is (= 1 (count errors)))
    (is (= {:extensions {:allowed-values #{:EMPIRE
                                           :JEDI
                                           :NEWHOPE}
                         :argument :__Queries/hero.episode
                         :enum-type :episode
                         :value :CLONES
                         :field :__Queries/hero}
            ;; TODO: This is the location of 'hero', should be location of 'episode' or 'CLONES'.
            :locations [{:column 3
                         :line 1}]
            :message "Exception applying arguments to field `hero': For argument `episode', provided argument value `CLONES' is not member of enum type."}
           first-error))))

(deftest enum-values-must-be-unique
  (expect-exception
    "Values defined for enum `invalid' must be unique."
    {:enum {:values [:yes 'yes "yes"]
            :category :enum
            :type-name :invalid}}
    (schema/compile {:enums {:invalid {:values [:yes 'yes "yes"]}}})))

(deftest converts-var-value-from-string-to-enum
  (is (= {:data {:hero {:name "Luke Skywalker"}}}
         (q "query ($ep : episode!) { hero (episode: $ep) { name }}"
            {:ep "NEWHOPE"}))))

(deftest resolver-must-return-defined-enum
  (let [schema (utils/compile-schema "bad-resolver-enum.edn"
                                     {:query/current-status (constantly :ok)})]
    (expect-exception
      "Field resolver returned an undefined enum value."
      {:enum-values #{:bad
                      :good}
       :resolved-value :ok}
      (utils/execute schema "{ current_status }"))))

(deftest enum-resolver-must-return-named-value
  (let [bad-value (Date.)
        schema (utils/compile-schema "bad-resolver-enum.edn"
                                     {:query/current-status (constantly bad-value)})]
    (expect-exception
      "Can't convert value to keyword."
      {:value bad-value}
      (utils/execute schema "{ current_status }"))))

(deftest will-convert-to-keyword
  (let [schema (utils/compile-schema "bad-resolver-enum.edn"
                                     {:query/current-status (constantly "good")})]
    (is (= {:data {:current_status :good}}
           (utils/execute schema "{ current_status }")))))

(deftest deprecated-enum-values
  (let [schema (utils/compile-schema "deprecated-enums-schema.edn" {})]
    (is (= {:data {:__type {:enumValues [{:deprecationReason "Should use happy."
                                          :description nil
                                          :isDeprecated true
                                          :name "GOOD"}
                                         {:deprecationReason nil
                                          :description "Desired state."
                                          :isDeprecated false
                                          :name "HAPPY"}
                                         {:deprecationReason nil
                                          :description nil
                                          :isDeprecated true
                                          :name "SAD"}]}}}
           (utils/execute schema
                          "{ __type(name: \"mood\") {
                                enumValues(includeDeprecated: true) {
                                  name description isDeprecated deprecationReason
                                }
                              }
                           }")))
    ;; Deprecated are ignored by default:

    (is (= {:data {:__type {:enumValues [{:deprecationReason nil
                                          :description "Desired state."
                                          :isDeprecated false
                                          :name "HAPPY"}]}}}
           (utils/execute schema
                          "{ __type(name: \"mood\") {
                                enumValues {
                                  name description isDeprecated deprecationReason
                                }
                              }
                           }")))))
