(ns com.walmartlabs.lacinia.resolver-errors-test
  "Tests for errors and exceptions inside field resolvers, and for the exception converter."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
    [com.walmartlabs.test-utils :refer [execute] :as utils])
  (:import (clojure.lang ExceptionInfo)))

(def ^:private failure-exception (ex-info "Fail!" {:reason :testing}))

(def ^:private resolver-map
  {:single-error (fn [_ _ _]
                   (resolve-as nil {:message "Exception in error_field resolver."}))
   :exception (fn [_ _ _]
                (throw failure-exception))
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

;; This now bubbles up and out with no special handling or reporting.
(deftest exception-inside-resolver
  (when-let [e (is (thrown? ExceptionInfo
                            (execute default-schema
                                     "{ root { exception (range: 5) }}")))]
    (is (identical? failure-exception e))))

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
