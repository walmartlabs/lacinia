(ns com.walmartlabs.lacinia.enums-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia :refer [execute]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-reporting :refer [report]]
    [com.walmartlabs.test-utils :refer [simplify]])
  (:import
    (clojure.lang ExceptionInfo)))

(def compiled-schema (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver}))



(defn q
  ([query]
    (q query nil))
  ([query vars]
   (simplify (execute compiled-schema query vars nil))))

(deftest returns-enums-as-keywords
  (is (= {:data {:hero {:appears_in [:NEWHOPE
                                     :EMPIRE
                                     :JEDI]
                        :name "R2-D2"}}}
         (q "{ hero { name appears_in }}"))))

(deftest can-provide-enum-as-bare-name
  (let [result (q "{ hero(episode: NEWHOPE) { name }}")
        hero-name (-> result :data :hero :name)]
    (report result
      (is (= "Luke Skywalker" hero-name)))))

(deftest handling-of-invalid-enum-value-as-argument
  (let [result (q "{ hero (episode: CLONES) { name }}")
        errors (-> result :errors)
        first-error (first errors)]
    (is (-> result (contains? :data) not))
    (is (= 1 (count errors)))
    (is (= {:allowed-values #{:EMPIRE
                              :JEDI
                              :NEWHOPE}
            :argument :episode
            :enum-type :episode
            :field :hero
            :locations [{:column 0
                         :line 1}]
            :message "Exception applying arguments to field `hero': For argument `episode', provided argument value `CLONES' is not member of enum type."
            :query-path []
            :value :CLONES}
           first-error))))

(deftest handling-of-invalid-enum-value-as-field
  (let [result (q "{ human (id: 1005) { appears_in } }")
        errors (:errors result)]
    (is (-> result (contains? :data) not))
    (is (= 1 (count errors)))))

(deftest enum-values-must-be-unique
  (let [e (is (thrown? ExceptionInfo
                       (schema/compile {:enums {:invalid {:values [:yes 'yes "yes"]}}})))]
    (is (= (.getMessage e)
           "Values defined for enum `invalid' must be unique."))
    (is (= {:enum {:values [:yes 'yes "yes"]
                   :category :enum
                   :type-name :invalid}}
           (ex-data e)))))


(deftest converts-var-value-from-string-to-enum
  (is (= {:data {:hero {:name "Luke Skywalker"}}}
         (q "query ($ep : episode!) { hero (episode: $ep) { name }}"
            {:ep "NEWHOPE"}))))
