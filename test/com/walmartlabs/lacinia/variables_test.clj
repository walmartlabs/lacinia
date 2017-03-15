(ns com.walmartlabs.lacinia.variables-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia :refer [execute execute-parsed-query]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.parser :as parser]))

(def ^:dynamic *schema* nil)

(use-fixtures :once
  (fn [f]
    (binding [*schema* (schema/compile test-schema)]
      (f))))

(deftest variables-can-have-default-values
  (let [q (parser/parse-query *schema*
                              "query ($id : String =  \"2001\") {

                                 droid (id : $id) { name }

                               }")]
    (is (= {:data {:droid {:name "R2-D2"}}}
           (execute-parsed-query *schema* q nil nil)))
    (is (= {:data {:droid {:name "C-3PO"}}}
           (execute-parsed-query *schema* q {:id "2000"} nil)))))

(deftest fragments-can-reference-variables
  (let [q (parser/parse-query *schema*
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
           (execute-parsed-query *schema* q nil nil)))
    (is (= {:data {:droid {:name "C-3PO"
                           :best_friend {:name "Luke Skywalker"}}}}
           (execute-parsed-query *schema* q {:terse false
                                             :id "2000"} nil)))))
