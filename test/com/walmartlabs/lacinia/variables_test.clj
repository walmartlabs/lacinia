(ns com.walmartlabs.lacinia.variables-test
  (:require
    [clojure.test :refer [deftest is are]]
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
           (execute-parsed-query q nil nil)))
    (is (= {:data {:droid {:name "C-3PO"
                           :best_friend {:name "Luke Skywalker"}}}}
           (execute-parsed-query q {:terse false
                                             :id "2000"} nil)))))

(def ^:private vars-schema
  (compile-schema "variables-schema.edn"
                  {:root-resolve (fn [_ args _] args)}))

(defn ^:private query
  [q vars]
  (execute vars-schema q vars nil))

(deftest int-rejects-non-integer-value
  (is (= {:errors [{:message "Invalid Int value."
                    :type-name :Int
                    :value "42.0"}]}
         (query "query ($IntVar: Int) { root (int: $IntVar) { int }}"
                {:IntVar 42.0}))))

(deftest int-rejects-out-of-range
  (are [v works?] (let [result (query "query ($IntVar: Int) { root (int: $IntVar) { int }}"
                                      {:IntVar v})]
                    (if works?
                      (is (contains? result :data)
                          ":data key should be present because value acceptible")
                      (is (and (contains? result :errors)
                               (not (contains? result :data)))
                          "only :errors key should be present due to failed parse")))

    Integer/MAX_VALUE true
    Integer/MIN_VALUE true

    (-> Integer/MIN_VALUE long dec) false
    (-> Integer/MAX_VALUE long inc) false))

(deftest support-for-float
  (are [v result-value] (let [result (query "query ($FloatVar: Float) { root (float: $FloatVar) { float }}"
                                            {:FloatVar v})]
                          (if (some? result-value)
                            (is (= result-value
                                   (get-in result [:data :root :float])))
                            (is (and (contains? result :errors)
                                     (not (contains? result :data))))))
    42.0 42.0
    (float -42.0) -42.0
    42 42.0
    1234M 1234.0
    "abc" nil
    "27" 27.0
    "98.6" 98.6))

(deftest boolean-parsing
  (are [v result-value] (let [result (query "query ($Flag : Boolean) { root (boolean: $Flag) { boolean }}"
                                            {:Flag v})]
                          (if (some? result-value)
                            (is (= result-value
                                   (get-in result [:data :root :boolean]))
                                (format "Expected %s output for input %s" result-value v))
                            (is (and (contains? result :errors)
                                     (not (contains? result :data)))
                                (format "Expected failure for input %s" v))))
    true true
    false false
    "true" true
    "false" false
    0 nil
    1 nil
    100 nil
    "xyz" nil))

(deftest boolean-serialization
  (are [v result-value] (let [result (query "query ($IntArg : Int) { int_args (boolean: $IntArg) { boolean }}"
                                            {:IntArg v})]
                          (is (= result-value
                                 (get-in result [:data :int_args :boolean]))
                              (format "Expected %s output for input %s" result-value v)))
    0 false
    1 true
    -1 true))
