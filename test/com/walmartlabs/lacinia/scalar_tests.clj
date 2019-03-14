; Copyright (c) 2019-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.scalar-tests
  (:require
    [clojure.test :refer :all]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :as utils]))

;; Although a lot of this testing could be "easier" by directly invoking the functions
;; exposed by schema/default-scalar-transformers, I prefer to verify that all the other machinery
;; around scalar parse/serialize is working.

(def ^:private ^:dynamic *data*)

(def ^:private output-schema
  (schema/compile {:objects
                   {:Scalars
                    {:fields {:id {:type :ID}
                              :string {:type :String}
                              :int {:type :Int}
                              :float {:type :Float}
                              :boolean {:type :Boolean}}}}

                   :queries
                   {:scalars {:type :Scalars
                              :resolve (fn [_ _ _] *data*)}}}))

(def ^:private id-schema
  (schema/compile {:queries
                   {:convert {:type :ID
                              :args {:value {:type :ID}}
                              :resolve (fn [_ args _]
                                         (:value args))}}}))
(deftest numeric-id-parsed-to-string
  (is (= {:data {:convert "232"}}
         (utils/execute id-schema "{ convert(value: 232) }"))))

(deftest string-id-passed-through-unchanged
  (is (= {:data {:convert "r2d2"}}
         (utils/execute id-schema "{ convert(value: \"r2d2\") }"))))

(deftest non-numeric-id-is-failure
  (is (= {:errors [{:extensions {:argument :value
                                 :field :convert
                                 :type-name :ID
                                 :value 3.41}
                    :locations [{:column 3
                                 :line 1}]
                    :message "Exception applying arguments to field `convert': For argument `value', unable to convert 3.41 to scalar type `ID'."}]}
         (utils/execute id-schema "{ convert(value: 3.41) }"))))

(deftest raw-id-must-be-string
  (binding [*data* {:id 2.0}]
    (is (= {:data {:scalars {:id nil}}
            :errors [{:extensions {:type-name :ID
                                   :value "2.0"}
                      :locations [{:column 13
                                   :line 1}]
                      :message "Unable to serialize 2.0 as type `ID'."
                      :path [:scalars
                             :id]}]}
           (utils/execute output-schema
                          "{ scalars { id } }")))))

(def ^:private int-schema
  (schema/compile {:queries
                   {:convert {:type :Int
                              :args {:value {:type :Int}}
                              :resolve (fn [_ args _]
                                         (:value args))}}}))
(deftest int-parse-only-numbers
  (is (= {:errors [{:extensions {:argument :value
                                 :field :convert
                                 :type-name :Int
                                 :value 98.6}
                    :locations [{:column 3
                                 :line 1}]
                    :message "Exception applying arguments to field `convert': For argument `value', unable to convert 98.6 to scalar type `Int'."}]}
         (utils/execute int-schema "{ convert(value: 98.6) }"))))

(deftest int-parse-in-range
  (doseq [v [Integer/MIN_VALUE Integer/MAX_VALUE 0]]
    (is (= {:data {:convert v}}
           (utils/execute int-schema (str "{ convert(value: " v ") }"))))))

(deftest int-too-small
  (is (= {:errors [{:extensions {:argument :value
                                 :field :convert
                                 :type-name :Int
                                 :value "-2147483649"}
                    :locations [{:column 3
                                 :line 1}]
                    :message "Exception applying arguments to field `convert': For argument `value', scalar value is not parsable as type `Int': Int value outside of allowed 32 bit integer range."}]}
         (utils/execute int-schema "{ convert(value: -2147483649) }"))))

(deftest int-too-large
  (is (= {:errors [{:extensions {:argument :value
                                 :field :convert
                                 :type-name :Int
                                 :value "2147483648"}
                    :locations [{:column 3
                                 :line 1}]
                    :message "Exception applying arguments to field `convert': For argument `value', scalar value is not parsable as type `Int': Int value outside of allowed 32 bit integer range."}]}
         (utils/execute int-schema "{ convert(value: 2147483648) }"))))

(deftest int-serialize-ok
  (doseq [v [Integer/MIN_VALUE Integer/MAX_VALUE 0
             (double Integer/MIN_VALUE) (double Integer/MAX_VALUE) 0.0]]
    (binding [*data* {:int v}]
      (is (= {:data {:scalars {:int (long v)}}}
             (utils/execute output-schema "{ scalars { int } }"))))))

(deftest int-serialize-non-numeric
  (binding [*data* {:int "not a number"}]
    (is (= {:data {:scalars {:int nil}}
            :errors [{:extensions {:type-name :Int
                                   :value "\"not a number\""}
                      :locations [{:column 13
                                   :line 1}]
                      :message "Unable to serialize \"not a number\" as type `Int'."
                      :path [:scalars
                             :int]}]}
           (utils/execute output-schema "{ scalars { int }}")))))

(deftest int-serialize-out-of-range
  (doseq [v [-2147483649 -2147483649.0 2147483648 2147483648.0]]
    (binding [*data* {:int v}]
      (is (= {:data {:scalars {:int nil}}
              :errors [{:extensions {:type-name :Int
                                     :value (str v)}
                        :locations [{:column 13
                                     :line 1}]
                        :message "Coercion error serializing value: Int value outside of allowed 32 bit integer range."
                        :path [:scalars
                               :int]}]}
             (utils/execute output-schema "{ scalars { int }}"))))))

(deftest int-serialize-non-whole-number
  (binding [*data* {:int 98.6}]
    (is (= {:data {:scalars {:int nil}}
            :errors [{:extensions {:type-name :Int
                                   :value "98.6"}
                      :locations [{:column 13
                                   :line 1}]
                      :message "Unable to serialize 98.6 as type `Int'."
                      :path [:scalars
                             :int]}]}
           (utils/execute output-schema "{ scalars { int }}")))))

(deftest float-serialize
  (binding [*data* {:float 98.6}]
    (is (= {:data {:scalars {:float 98.6}}}
           (utils/execute output-schema "{ scalars { float }}")))))

(deftest float-serialize-int
  (binding [*data* {:float 42}]
    (is (= {:data {:scalars {:float 42.0}}}
           (utils/execute output-schema "{ scalars { float }}")))))

(deftest float-serialize-numeric-string
  (binding [*data* {:float "98.6"}]
    (is (= {:data {:scalars {:float 98.6}}}
           (utils/execute output-schema "{ scalars { float }}")))))

(deftest float-serialize-non-numeric-string-fails
  (binding [*data* {:float "nope"}]
    (is (= {:data {:scalars {:float nil}}
            :errors [{:extensions {:type-name :Float
                                   :value "\"nope\""}
                      :locations [{:column 13
                                   :line 1}]
                      :message "Unable to serialize \"nope\" as type `Float'."
                      :path [:scalars
                             :float]}]}
           (utils/execute output-schema "{ scalars { float }}")))))

(def ^:private float-schema
  (schema/compile {:queries
                   {:convert {:type :Float
                              :args {:value {:type :Float}}
                              :resolve (fn [_ args _]
                                         (:value args))}}}))

(deftest float-parse
  (is (= {:data {:convert 123.456}}
         (utils/execute float-schema "{ convert(value: 123.456) }"))))

(deftest float-parse-int
  (is (= {:data {:convert 42.0}}
         (utils/execute float-schema "{ convert(value: 42) }"))))

(def ^:private string-schema
  (schema/compile {:queries
                   {:convert {:type :String
                              :args {:value {:type :String}}
                              :resolve (fn [_ args _]
                                         (:value args))}}}))

(deftest string-parse-only-string
  (is (= {:errors [{:extensions {:argument :value
                                 :field :convert
                                 :type-name :String
                                 :value 98.6}
                    :locations [{:column 3
                                 :line 1}]
                    :message "Exception applying arguments to field `convert': For argument `value', unable to convert 98.6 to scalar type `String'."}]}
         (utils/execute string-schema "{ convert(value: 98.6) }"))))


(def ^:private boolean-schema
  (schema/compile {:queries
                   {:convert {:type :Boolean
                              :args {:value {:type :Boolean}}
                              :resolve (fn [_ args _]
                                         (:value args))}}}))

(deftest boolean-parse
  (is (= {:data {:convert true}}
         (utils/execute boolean-schema "{ convert(value: true) }"))))

(deftest boolean-parse-only-boolean
  (is (= {:errors [{:extensions {:argument :value
                                 :field :convert
                                 :type-name :Boolean
                                 :value "sure"}
                    :locations [{:column 3
                                 :line 1}]
                    :message "Exception applying arguments to field `convert': For argument `value', unable to convert \"sure\" to scalar type `Boolean'."}]}
         (utils/execute boolean-schema "{ convert(value: \"sure\") }"))))
