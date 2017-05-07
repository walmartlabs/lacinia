(ns com.walmartlabs.lacinia.custom-scalars-test
  (:require [clojure.spec :as s]
            [clojure.test :refer [deftest is testing]]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.test-schema :refer [test-schema]]
            [com.walmartlabs.test-utils :refer [is-thrown instrument-schema-namespace simplify]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia.util :as util])
  (:import (java.text SimpleDateFormat)
           (java.util Date)
           (org.joda.time DateTime DateTimeConstants)
           (org.joda.time.format DateTimeFormatter DateTimeFormat)))

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
        parse-conformer (s/conformer (fn [input]
                                       (try (.parse date-formatter input)
                                            (catch Exception e
                                              :clojure.spec/invalid))))
        serialize-conformer (s/conformer (fn [output] (.format date-formatter output)))
        schema (schema/compile
                {:scalars {:Date {:parse parse-conformer
                                  :serialize serialize-conformer}}

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

(defn ^:private periodic-seq
  [^Date start ^Date end]
  (take-while (fn [^Date date]
                (not (.isAfter date end)))
              (map (fn [i] (.plusDays start i)) (iterate inc 0))))

(defn ^:private  sunday? [t]
  (= (.getDayOfWeek t) (DateTimeConstants/SUNDAY)))

(defn ^:private sundays [start end]
  (let [date-range (if (and start end) (periodic-seq start end) [])]
    (filter sunday? date-range)))

(deftest custom-scalars-with-complex-types
  (let [date-formatter (DateTimeFormat/forPattern "yyyy-MM-dd")
        parse-conformer (s/conformer (fn [input]
                                       (try (DateTime. (.parseDateTime date-formatter input))
                                            (catch Exception e
                                              :clojure.spec/invalid))))
        serialize-conformer (s/conformer (fn [output] (.print date-formatter output)))
        schema (schema/compile {:scalars {:Date {:parse parse-conformer
                                                  :serialize serialize-conformer}}
                                :queries {:sundays {:type '(list (non-null :Date))
                                                    :args {:between {:type '(non-null (list (non-null :Date)))}}
                                                    :resolve (fn [ctx args v]
                                                               (let [[start end] (:between args)]
                                                                 (sundays start end)))}}})]
    (is (= {:data {:sundays ["2017-03-05" "2017-03-12" "2017-03-19" "2017-03-26"]}}
           (execute schema "query ($between: [Date!]!) {
                              sundays(between: $between)
                            }"
                    {:between ["2017-03-05" "2017-03-30"]}
                    nil))
        "should return list of serialized dates")
    (is (= {:data {:sundays []}}
           (execute schema "query ($between: [Date!]!) {
                              sundays(between: $between)
                            }"
                    {:between ["2017-03-06" "2017-03-07"]}
                    nil))
        "should return empty list")
    (is (= {:data {:sundays []}}
           (execute schema "query ($between: [Date!]!) {
                              sundays(between: $between)
                            }"
                    {:between []}
                    nil))
        "should return empty list (:between can be an empty list) ")
    (is (= {:errors [{:message "No value was provided for variable `between', which is non-nullable.",
                      :variable-name :between}]}
           (execute schema "query ($between: [Date!]!) {
                              sundays(between: $between)
                            }"
                    {:between nil}
                    nil))
        "should return an error")
    (is (= {:errors [{:message "Variable `between' contains null members but supplies the value for a list that can't have any null members."
                      :variable-name :between}]}
           (execute schema "query ($between: [Date!]!) {
                              sundays(between: $between)
                            }"
                    {:between [nil]}
                    nil))
        "should return an error")
    (is (= {:errors [{:message "Variable `between' contains null members but supplies the value for a list that can't have any null members."
                      :variable-name :between}]}
           (execute schema "query ($between: [Date!]!) {
                              sundays(between: $between)
                            }"
                    {:between ["2017-03-01" nil]}
                    nil))
        "should return an error")
    (is (= {:errors [{:message "No value was provided for variable `between', which is non-nullable."
                      :variable-name :between}]}
           (execute schema "query ($between: [Date!]!) {
                              sundays(between: $between)
                            }"
                    nil
                    nil))
        "should return an error"))

  (testing "nested lists"
    (let [scalars {:CustomType {:parse (s/conformer (fn [x] (name x)))
                                :serialize (s/conformer (fn [x] (.toUpperCase x)))}}
          schema (schema/compile {:scalars scalars
                                  :queries {:shout {:type '(list (list (list :CustomType)))
                                                    :args {:words {:type '(list (list (list :CustomType)))}}
                                                    :resolve (fn [ctx args v]
                                                               (:words args))}}})]
      (is (= {:data {:shout [[["FOO" "BAR"]]]}}
             (execute schema "query ($words: [[[CustomType]]]) {
                              shout(words: $words)
                            }"
                      {:words [[["foo" "bar"]]]}
                      nil))
          "should return nested list")
      (is (= {:errors [{:message "Variable `words' doesn't contain the correct number of (nested) lists.",
                        :variable-name :words}]}
             (execute schema "query ($words: [[[CustomType]]]) {
                              shout(words: $words)
                            }"
                      {:words [["foo" "bar"]]}
                      nil))
          "should return an error")
      (is (= {:data {:shout []}}
             (execute schema "query ($words: [[[CustomType]]]) {
                              shout(words: $words)
                            }"
                      {:words nil}
                      nil))
          "should return empty list")))

  (testing "nested list with a non-null root element in query result"
    (let [scalars {:CustomType {:parse (s/conformer (fn [x] (name x)))
                                :serialize (s/conformer (fn [x] (.toUpperCase x)))}}
          schema (schema/compile {:scalars scalars
                                  :queries {:shout {:type '(list (list (list (non-null :CustomType))))
                                                    :args {:words {:type '(list (list (list :CustomType)))}}
                                                    :resolve (fn [ctx args v]
                                                               (:words args))}}})]
      (is (= {:data {:shout [[[nil]]]},
              :errors [{:message "Non-nullable field was null.",
                        :locations [{:line 1, :column 33}],
                        :query-path [:shout],
                        :arguments {:words '$words}}]}
             (execute schema "query ($words: [[[CustomType]]]) {
                              shout(words: $words)
                            }"
                      {:words [[[nil]]]}
                      nil))
          "should return an error")
      (is (= {:errors [{:message"Variable `words' doesn't contain the correct number of (nested) lists.",
                        :variable-name :words}]}
             (execute schema "query ($words: [[[CustomType]]]) {
                              shout(words: $words)
                            }"
                      {:words [[nil]]}
                      nil))
          "should return an error")
      (is (= {:data {:shout [[["BAR"]]]}}
             (execute schema "query ($words: [[[CustomType]]]) {
                              shout(words: $words)
                            }"
                      {:words [[["bar"]]]}
                      nil))
          "should return data")))

  (testing "nested list with a non-null root element in query args"
    (let [scalars {:CustomType {:parse (s/conformer (fn [x] (name x)))
                                :serialize (s/conformer (fn [x] (.toUpperCase x)))}}
          schema (schema/compile {:scalars scalars
                                  :queries {:shout {:type '(list (list (list :CustomType)))
                                                    :args {:words {:type '(list (list (list (non-null :CustomType))))}}
                                                    :resolve (fn [ctx args v]
                                                               (:words args))}}})]
      (is (= {:errors [{:message "Variable `words' contains null members but supplies the value for a list that can't have any null members.",
                        :variable-name :words}]}
             (execute schema "query ($words: [[[CustomType!]]]) {
                              shout(words: $words)
                            }"
                      {:words [[[nil]]]}
                      nil))
          "should return an error")
      (is (= {:errors [{:message"Variable `words' doesn't contain the correct number of (nested) lists.",
                        :variable-name :words}]}
             (execute schema "query ($words: [[[CustomType!]]]) {
                              shout(words: $words)
                            }"
                      {:words [["foo"]]}
                      nil))
          "should return an error")
      (is (= {:errors [{:message "Exception applying arguments to field `shout': For argument `words', variable and argument are not compatible types.",
                        :query-path []
                        :locations [{:line 1 :column 33}]
                        :field :shout
                        :argument :words
                        :argument-type "[[[CustomType!]]]"
                        :variable-type "[[[CustomType]]]"}]}
             (execute schema "query ($words: [[[CustomType]]]) {
                              shout(words: $words)
                            }"
                      {:words [["foo"]]}
                      nil))
          "should return an error")
      (is (= {:data {:shout [[["FOO"]]]}}
             (execute schema "query ($words: [[[CustomType!]]]) {
                              shout(words: $words)
                            }"
                      {:words [[["foo"]]]}
                      nil))
          "should return an error")))

  (testing "nested non-null lists with a root list allowed to contain nulls in query args"
    (let [scalars {:CustomType {:parse (s/conformer (fn [x] (if (some? x)
                                                              (name x)
                                                              :clojure.spec/invalid)))
                                :serialize (s/conformer (fn [x] (when x (.toUpperCase x))))}}
          schema (schema/compile {:scalars scalars
                                  :queries {:shout {:type '(list (list (list :CustomType)))
                                                    :args {:words {:type '(non-null (list (non-null (list (non-null (list :CustomType))))))}}
                                                    :resolve (fn [ctx args v]
                                                               (:words args))}}})]
      (is (= {:data {:shout [[[nil]]]}}
             (execute schema "query ($words: [[[CustomType]!]!]!) {
                              shout(words: $words)
                            }"
                      {:words [[[nil]]]}
                      nil))
          "should return data")
      (is (= {:data {:shout [[["FOO"]]]}}
             (execute schema "query ($words: [[[CustomType]!]!]!) {
                              shout(words: $words)
                            }"
                      {:words [[["foo"]]]}
                      nil))
          "should return data")
      (is (= {:errors [{:message "Variable `words' doesn't contain the correct number of (nested) lists.",
                        :variable-name :words}]}
             (execute schema "query ($words: [[[CustomType]!]!]!) {
                              shout(words: $words)
                            }"
                      {:words [[[] "foo"]]}
                      nil))
          "should return an error"))))

(deftest use-of-coercion-error
  (let [schema (-> "custom-scalar-serialize-schema.edn"
                   io/resource
                   slurp
                   edn/read-string
                   (util/attach-resolvers {:test-query
                                           (fn [_ args _]
                                             (:in args))})
                   (util/attach-scalar-transformers
                     {:parse (schema/as-conformer (fn [s]
                                                    (if (= "5" s)
                                                      (schema/coercion-failure "Just don't like 5.")
                                                      (Integer/parseInt s))))
                      :serialize (schema/as-conformer
                                   (fn [v]
                                     (if (< v 5)
                                       v
                                       (schema/coercion-failure "5 is too big."))))})
                   schema/compile)]
    (testing "parsers"
      (is (= {:data {:dupe 4}}
             (execute schema
                      "{ dupe (in:4) }"
                      nil
                      nil)))

      (is (= {:errors [{:argument :in
                        :field :dupe
                        :locations [{:column 0
                                     :line 1}]
                        :message "Exception applying arguments to field `dupe': For argument `in', just don't like 5."
                        :query-path []
                        :type-name :LimitedInt
                        :value "5"}]}
             (execute schema
                      "{ dupe (in: 5) }"
                      nil
                      nil))))

    (testing "serializers"
      (is (= {:data {:test 4}}
             (execute schema
                      "{ test (in:4) }"
                      nil
                      nil)))


      (is (= {:data {:test nil}
              :errors [{:arguments {:in "5"}
                        :locations [{:column 0
                                     :line 1}]
                        :message "5 is too big."
                        :query-path [:test]}]}
             (execute schema
                      "{ test (in:5) }"
                      nil
                      nil))))))
