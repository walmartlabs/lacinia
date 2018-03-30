(ns com.walmartlabs.lacinia.arguments-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [compile-schema]])
  (:import (clojure.lang ExceptionInfo)))


(deftest reports-unknown-argument-type
  (when-let [e (is (thrown? ExceptionInfo
                            (compile-schema "unknown-argument-type-schema.edn"
                                            {:example identity})))]
    (is (= "Argument `id' of field `__Queries/example' references unknown type `UUID'."
           (.getMessage e)))
    (is (= {:arg-name :id
            :field-name :__Queries/example
            :schema-types {:object [:MutationRoot
                                    :QueryRoot
                                    :SubscriptionRoot]
                           :scalar [:Boolean
                                    :Float
                                    :ID
                                    :Int
                                    :String]}}
           (ex-data e)))))
