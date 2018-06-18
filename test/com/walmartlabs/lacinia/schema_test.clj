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

(ns com.walmartlabs.lacinia.schema-test
  "Tests schema functions."
  (:require
    [clojure.test :refer [deftest testing is are try-expr do-report]]
    [com.walmartlabs.test-reporting :refer [reporting]]
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.executor :as executor]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.test-utils :refer [is-thrown]]
    [clojure.string :as str]
    [clojure.pprint :as pprint]))

(defmacro is-error?
  [form]
  `(let [tuple# (try-expr "Invoking enforcer." ~form)]
     (when-not (-> tuple# second some?)
       (do-report {:type    :fail
                   :message "Expected some errors in the resolved tuple"}))))



(deftest schema-shape
  (testing "schema with not required field"
    (let [s {:objects
             {:person
              {:fields {:foo {:type 'String}}
               :bar "I'm extra"}}}]
      (is (seq (schema/compile s))
          "should compile schema without any problems"))))

(def schema-object-references-unknown-interface
  {:interfaces
   {:fred
    {}

    :barney
    {}}

   :objects
   {:dino
    {:implements [:fred :barney :bam_bam :pebbles]
     :fields {}}}})

(def schema-references-unknown-type
  {:interfaces
   {:fred
    {}

    :barney
    {}}

   :objects
   {:dino
    {:implements [:fred :barney]
     :fields {:dinosaur {:type :raptor}}}}})

(def schema-generated-data
  [:one :two :three])

(defn schema-generated-resolver [context args value]
  (keys (executor/selections-tree context)))

(def schema-generated-lists
  {:objects
   (into {}
     (for [f schema-generated-data]
       [f {:fields {:name {:type 'String}}}]))
   :queries
   (into {}
     (for [f schema-generated-data]
       [f {:type `(~'list ~f)
           :resolve :schema-generated-resolver}]))})

(deftest invalid-schemas
  []
  (is-thrown [e (schema/compile schema-object-references-unknown-interface)]
             (let [d (ex-data e)]
               ;; I like to check individual properties one at a time
               ;; since the resulting failure is easier to understand.
               (is (= (.getMessage e) "Object `dino' extends interface `pebbles', which does not exist."))
               (is (= :dino (-> d :object :type-name)))
               (is (= [:fred :barney :bam_bam :pebbles] (-> d :object :implements)))))

  (is-thrown [e (schema/compile schema-references-unknown-type)]
    (is (= (.getMessage e) "Field `dino/dinosaur' references unknown type `raptor'.")))

  (is (schema/compile
        (util/attach-resolvers
          schema-generated-lists
          {:schema-generated-resolver schema-generated-resolver}))))

(deftest printing-support
  (let [compiled-schema (schema/compile {})
        as-map (into {} compiled-schema)]
    (is (= "#CompiledSchema<>"
           (pr-str compiled-schema)))

    (is (= "#CompiledSchema<>"
           (pprint/write compiled-schema :stream nil)))

    (binding [schema/*verbose-schema-printing* true]
      (is (= (pr-str as-map)
             (pr-str compiled-schema)))

      (is (= (pprint/write as-map :stream nil)
             (pprint/write compiled-schema :stream nil))))))

(deftest custom-scalars
  []
  (testing "custom scalars defined as conformers"
    (let [parse-conformer (s/conformer
                           (fn [x]
                             (case x
                               "200" "OK"
                               "500" "ERROR"
                               ::s/invalid)))
          serialize-conformer (s/conformer
                               (fn [x]
                                 (case x
                                   "OK" "200"
                                   "ERROR" "500"
                                   ::s/invalid)))]
      (is (schema/compile {:scalars
                           {:Event {:parse parse-conformer
                                    :serialize serialize-conformer}}

                           :queries
                           {:events {:type :Event
                                     :resolve (constantly "200")}}}))
      (is-thrown [e (schema/compile {:scalars
                                     {:Event {:parse str
                                              :serialize serialize-conformer}}

                                     :queries
                                     {:events {:type :Event
                                               :resolve (constantly "200")}}})]
                 (let [problems (:clojure.spec.alpha/problems (ex-data e))]
                   (is (= 1 (count problems))
                       "should find one invalid conformer")
                   (is (= {:in [0
                                :scalars
                                :Event
                                1
                                :parse]
                           :path [:schema
                                  :scalars
                                  1
                                  :parse]}
                          (-> problems first (select-keys [:path :in])))
                       "should find problem in parse conformer"))))))

(defmacro is-compile-exception
  [schema expected-message]
  `(is-thrown [e# (schema/compile ~schema)]
     (let [msg# (.getMessage e#)]
       (reporting {:message msg#}
         (is (str/includes? msg# ~expected-message))))))

(deftest types-must-be-valid-ids
  (is-compile-exception
    {:objects {:not-valid-id {:fields {:id {:type :String}}}}}
    "must be a valid GraphQL identifier"))

(deftest field-names-must-be-valid-ids
  (is-compile-exception
    {:queries {:invalid-field-name {:type :String
                              :resolve identity}}}
    "must be a valid GraphQL identifier"))

(deftest enum-values-must-be-valid-ids
  (is-compile-exception
    {:enums {:episode {:values [:new-hope :empire :return-of-jedi]}}}
    "must be a valid GraphQL identifier"))

(deftest requires-resolve-on-operation
  (is-compile-exception
    {:queries {:hopeless {:type :String}}}
    "should contain key: :resolve"))
