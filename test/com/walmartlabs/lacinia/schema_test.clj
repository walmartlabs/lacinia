(ns com.walmartlabs.lacinia.schema-test
  "Tests schema functions."
  (:require
    [clojure.test :refer [deftest testing is are try-expr do-report]]
    [clojure.spec :as s]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
    [com.walmartlabs.test-utils :refer [is-thrown instrument-schema-namespace]]))

(instrument-schema-namespace)

(defmacro is-error?
  [form]
  `(let [tuple# (try-expr "Invoking enforcer." ~form)]
     (when-not (-> tuple# second some?)
       (do-report {:type    :fail
                   :message "Expected some errors in the resolved tuple"}))))

(deftest reject-nil-enforcer
  (let [input (vector 42)]
    (is (= input
           (#'schema/reject-nil-enforcer input))))

  (is-error? (#'schema/reject-nil-enforcer (vector nil))))

(deftest enforce-single-result
  (let [input (vector :just-me)]
    (is (= input
           (#'schema/enforce-single-result input))))

  (is-error? (#'schema/enforce-single-result (resolve-as [1 2 3]))))

(deftest map-enforcer
  (let [enforcer (#'schema/map-enforcer #'schema/reject-nil-enforcer)]

    (are [input-value output-value]

      (= (vector output-value nil) (enforcer (vector input-value)))

               [1 2 3] [1 2 3]
               [] []
        nil [])

    (let [tuple (enforcer (vector [1 nil 2 nil 3]))]
      (is (= [1 nil 2 nil 3]
             (first tuple)))
      ;; Redundant errors used to be removed by map-enforcer,
      ;; but that has moved to the wrap-resolve-with-enforcer code.
      (is (= 1
             (-> tuple second distinct count))))))

(deftest composition
  (let [composed (#'schema/compose-enforcers
                  #'schema/reject-nil-enforcer
                  #'schema/enforce-single-result)]

    (is (= (vector 33)
           (composed (vector 33))))

    (is-error? (composed (vector nil)))
    (is-error? (composed (vector [1 2 3])))))

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
    {:implements [:fred :barney :bam-bam :pebbles]
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
               (is (= (.getMessage e) "Object `dino' extends interface `bam-bam', which does not exist."))
               (is (= :dino (-> d :object :type-name)))
               (is (= [:fred :barney :bam-bam :pebbles] (-> d :object :implements)))))

  (is-thrown [e (schema/compile schema-references-unknown-type)]
    (is (= (.getMessage e) "Field `dinosaur' of type `dino' references unknown type `raptor'."))))

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
                 (let [problems (:clojure.spec/problems (ex-data e))]
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
