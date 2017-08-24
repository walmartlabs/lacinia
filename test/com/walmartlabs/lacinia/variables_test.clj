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

(defn with-tag
  [v]
  (if-let [type (::type v)]
    (schema/tag-with-type v type)
    v))

(deftest variables-with-escape-sequences
  (let [value (atom nil)
        schema (schema/compile
                {:objects {:value {:fields {:value {:type 'String}}}}
                 :mutations {:change {:type :value
                                      :args {:newValue {:type 'String}}
                                      :resolve (fn [ctx args v]
                                                 (reset! value (:newValue args))
                                                 {:value @value})}}})
        q (parser/parse-query schema "mutation ($ValueArg: String) { change(newValue: $ValueArg) { value }}")]
    (is (= (execute-parsed-query q {:ValueArg "\\\"\\u2B50\\\""} nil)
           {:data {:change {:value "\"⭐\""}}}))))

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
        (is (= {:data {:villain {:name "Darth Vader"}}}
               (execute-parsed-query q {:episode nil} nil))
            "should return default value when variable is null")
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
