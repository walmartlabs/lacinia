(ns com.walmartlabs.lacinia.schema-test
  "Tests schema functions."
  (:require
    [clojure.test :refer [deftest testing is are try-expr do-report]]
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :refer [is-thrown]]
    [clojure.string :as str]))

(defmacro is-error?
  [form]
  `(let [tuple# (try-expr "Invoking enforcer." ~form)]
     (when-not (-> tuple# second some?)
       (do-report {:type    :fail
                   :message "Expected some errors in the resolved tuple"}))))



(deftest schema-shape
  ;; We should renable this test if we make :fields required again
  ;; (which would imply that objects no longer inherit fields from interfaces).
  #_ (testing "schema with missing required keys"
    (let [s {:objects
             {:person
              {:description "I'm an object without fields"}}}]
      (is-thrown [e (schema/compile s)]
                 (let [d (ex-data e)]
                   (is (= (:in (first (:clojure.spec/problems d))) [:objects :person 1]))
                   (is (= (.getMessage e) "Invalid schema object definition."))))))
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
    (is (= (.getMessage e) "Field `dino/dinosaur' references unknown type `raptor'."))))

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
                           :path [:args
                                  :schema
                                  :scalars
                                  1
                                  :parse]}
                          (-> problems first (select-keys [:path :in])))
                       "should find problem in parse conformer"))))))

(defmacro is-compile-exception
  [schema expected-message]
  `(is-thrown [e# (schema/compile ~schema)]
     (is (str/includes? (.getMessage e#)
                        ~expected-message))))

;; (deftest types-must-be-valid-ids
;;   (is-compile-exception
;;     {:objects {:not-valid-id {:fields {:id {:type :String}}}}}
;;     ":not-valid-id fails spec: :com.walmartlabs.lacinia.schema/graphql-identifier"))

;; (deftest field-names-must-be-valid-ids
;;   (is-compile-exception
;;     {:queries {:invalid-field-name {:type :String
;;                               :resolve identity}}}
;;     ":invalid-field-name fails spec: :com.walmartlabs.lacinia.schema/graphql-identifier"))

;; (deftest enum-values-must-be-valid-idfs
;;   (is-compile-exception
;;     {:enums {:episode {:values [:new-hope :empire :return-of-jedi]}}}
;;     "[:keyword :new-hope] fails spec: :com.walmartlabs.lacinia.schema/enum-value at: [:args :schema :enums 1 :values] predicate: graphql-identifier?"))

(deftest requires-resolve-on-operationfv
  (is-compile-exception
    {:queries {:hopeless {:type :String}}}
    "predicate: (contains? % :resolve)"))
