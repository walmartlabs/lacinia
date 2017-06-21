(ns com.walmartlabs.lacinia.resolver-errors-test
  "Tests for errors and exceptions inside field resolvers, and for the exception converter."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
    [com.walmartlabs.test-utils :refer [execute]
     :as utils]
    [com.walmartlabs.lacinia.util :as util]))

(def ^:private resolver-map
  {:single-error (fn [_ _ _]
                   (resolve-as nil {:message "Exception in error_field resolver."}))
   :exception (fn [_ _ _]
                (throw (ex-info "Fail!"
                                {:reason :testing})))
   :multiple-errors (fn [_ _ _]
                      (resolve-as "Value"
                                  [{:message "1" :other-key 100}
                                   {:message "2"}
                                   {:message "3"}
                                   {:message "4"}]))
   :resolve-root (fn [_ _ _] {})})

(def default-schema
  (utils/compile-schema "field-resolver-errors.edn"
                        resolver-map))

(deftest exception-inside-resolver
  (is (= {:data {:root {:exception nil}}
          :errors [{:locations [{:column 7
                                 :line 1}]
                    ;; Notice that the argument values are quoted
                    ;; to ensure that they can be reported back via
                    ;; JSON.
                    :arguments {:range "5"}
                    :message "Fail!"
                    :query-path [:root
                                 :exception]
                    :reason :testing}]}
         (execute default-schema
                  "{ root { exception (range: 5) }}"))))

(deftest field-with-single-error
  (is (= {:data {:root {:error_field nil}}
          :errors [{:locations [{:column 7
                                 :line 1}]
                    :message "Exception in error_field resolver."
                    :query-path [:root
                                 :error_field]}]}
         (execute default-schema
                  "{ root { error_field }}"))))

(deftest field-with-multiple-errors
  (is (= [{:locations [{:column 7
                        :line 1}]
           :message "1"
           :other-key 100
           :query-path [:root
                        :multiple_errors_field]}
          {:locations [{:column 7
                        :line 1}]
           :message "2"
           :query-path [:root
                        :multiple_errors_field]}
          {:locations [{:column 7
                        :line 1}]
           :message "3"
           :query-path [:root
                        :multiple_errors_field]}
          {:locations [{:column 7
                        :line 1}]
           :message "4"
           :query-path [:root
                        :multiple_errors_field]}]
         (->> (execute default-schema "{ root { multiple_errors_field }}")
              :errors
              (sort-by :message)))))

(deftest exception-converter
  (let [converter (fn [qname fargs exception]
                    (util/as-error-map exception
                                       {::qname qname
                                        ::fargs fargs}))
        schema (utils/compile-schema "field-resolver-errors.edn"
                                     resolver-map
                                     {:exception-converter converter})]
    (is (= {:data {:root {:exception nil}}
            :errors [{:arguments {:range "20"}
                      ;; Note: the actual map of argument values
                      ::fargs {:range 20}
                      ::qname :MyObject/exception
                      :locations [{:column 7
                                   :line 1}]
                      :message "Fail!"
                      :query-path [:root
                                   :exception]
                      :reason :testing}]}
           (execute schema "{ root { exception (range: 20) }}")))))
