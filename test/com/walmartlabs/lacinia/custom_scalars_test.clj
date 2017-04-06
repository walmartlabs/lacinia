(ns com.walmartlabs.lacinia.custom-scalars-test
  (:require  [clojure.test :as t]
             [clojure.spec :as s]
             [clojure.test :refer [deftest is testing]]
             [com.walmartlabs.lacinia :as lacinia]
             [com.walmartlabs.lacinia.schema :as schema]
             [com.walmartlabs.test-schema :refer [test-schema]]
             [com.walmartlabs.test-utils :refer [is-thrown instrument-schema-namespace simplify]])
  (:import (java.text SimpleDateFormat)
           (java.util Date)))

(instrument-schema-namespace)

(def default-schema (schema/compile test-schema))

(defn execute
  "Executes the query but reduces ordered maps to normal maps, which makes
  comparisons easier.  Other tests exist to ensure that order is maintained."
  [schema q vars context]
  (-> (lacinia/execute schema q vars context)
      simplify))

;;-------------------------------------------------------------------------------
;; ## Tests

(deftest custom-scalar-query
  (let [q "{ now { date }}"]
    (is (= {:data {:now {:date "A long time ago"}}}
           (execute default-schema q nil nil)))))

(deftest custom-scalars
  (testing "custom scalars defined as conformers"
    (let [parse-conformer (s/conformer
                           (fn [x]
                             (prn "qoooooooo")
                             (if (and
                                  (string? x)
                                  (< (count x) 3))
                               x
                               :clojure.spec/invalid)))
          serialize-conformer (s/conformer
                               (fn [x]
                                 (case x
                                   "200" "OK"
                                   "500" "ERROR"
                                   :clojure.spec/invalid)))]
      (testing "custom scalar's serializing option"
        (let [schema (schema/compile {:scalars
                                      {:Event {:parse parse-conformer
                                               :serialize serialize-conformer}}

                                      :objects
                                      {:galaxy-event
                                       {:fields {:lookup {:type :Event}}}}

                                      :queries
                                      {:events {:type :galaxy-event
                                                :resolve (fn [ctx args v]
                                                           {:lookup "200"})}}})
              q "{ events { lookup }}"]
          (is (= {:data {:events {:lookup "OK"}}} (execute schema q nil nil))
              "should return conformed value")))
      (testing "custom scalar's invalid value"
        (let [schema (schema/compile {:scalars
                                      {:Event {:parse parse-conformer
                                               :serialize serialize-conformer}
                                       :EventId {:parse parse-conformer
                                                 :serialize (s/conformer str)}}

                                      :objects
                                      {:galaxy-event
                                       {:fields {:lookup {:type :Event}}}
                                       :human
                                       {:fields {:id {:type :EventId}
                                                 :name {:type 'String}}}}

                                      :queries
                                      {:events {:type :galaxy-event
                                                :resolve (fn [ctx args v]
                                                           ;; type of :lookup is :Event
                                                           ;; that is a custom scalar with
                                                           ;; a serialize function that
                                                           ;; deems anything other than
                                                           ;; "200" or "500" invalid.
                                                           ;; So value 1 should cause
                                                           ;; an error.
                                                           {:lookup 1})}
                                       :human {:type '(non-null :human)
                                               :args {:id {:type :EventId}}
                                               :resolve (fn [ctx args v]
                                                          {:id "1000"
                                                           :name "Luke Skywalker"})}}})
              q1 "{ human(id: \"1003\") { id, name }}"
              q2 "{ events { lookup }}"]
          (is (= {:errors [{:argument :id
                            :field :human
                            :locations [{:column 0
                                         :line 1}]
                            :message "Exception applying arguments to field `human': For argument `id', scalar value is not parsable as type `EventId'."
                            :query-path []
                            :type-name :EventId
                            :value "1003"}]}
                 (execute schema q1 nil nil))
              "should return error message")
          (is (= {:data {:events {:lookup nil}}
                  :errors [{:locations [{:column 9
                                         :line 1}]
                            :message "Invalid value for a scalar type."
                            :query-path [:events
                                         :lookup]
                            :type :Event
                            :value "1"}]}
                 (execute schema q2 nil nil))
              "should return error message"))))))

(deftest custom-scalars-with-variables
  (let [date-formatter (SimpleDateFormat. "yyyy-MM-dd")
        parse-fn (s/conformer (fn [input] (try (.parse date-formatter input)
                                               (catch Exception e
                                                 :clojure.spec/invalid))))
        serialize-fn (s/conformer (fn [output] (.format date-formatter output)))
        schema (schema/compile
                {:scalars {:Date {:parse parse-fn
                                  :serialize serialize-fn}}

                 :queries {:today {:type :Date
                                   :args {:asOf {:type :Date}}
                                   :resolve (fn [ctx args v] (:asOf args))}}})]
    (is (= {:data {:today "2017-04-05"}}
           (execute schema "query ($asOf : Date =  \"2017-04-05\") {
                              today (asOf: $asOf)
                            }"
                    nil
                    nil))
        "should return parsed and serialized value")
    (is (= {:data {:today "2017-04-05"}}
           (execute schema "query ($asOf: Date) {
                              today(asOf: $asOf)
                            }"
                    {:asOf "2017-04-05"}
                    nil))
        "should return parsed and serialized value")
    (is (= {:errors [{:message "Scalar value is not parsable as type `Date'.",
                      :value "abc",
                      :type-name :Date}]}
           (execute schema "query ($asOf: Date) {
                              today(asOf: $asOf)
                            }"
                    {:asOf "abc"}
                    nil))
        "should return error message")))
