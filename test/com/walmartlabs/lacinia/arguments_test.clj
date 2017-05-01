(ns com.walmartlabs.lacinia.arguments-test
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.schema :as schema]
    [clojure.java.io :as io])
  (:import (clojure.lang ExceptionInfo)))


(deftest reports-unknown-argument-type
  (when-let [e (is (thrown? ExceptionInfo
                            (-> "unknown-argument-type-schema.edn"
                                io/resource
                                slurp
                                edn/read-string
                                schema/compile)))]
    (is (= "Argument `id' of field `example' in type `QueryRoot' references unknown type `UUID'."
           (.getMessage e)))
    (is (= {:arg-name :id
            :field-name :example
            :object-type :QueryRoot
            :schema-types {:object [:MutationRoot
                                    :QueryRoot]
                           :scalar [:Boolean
                                    :Float
                                    :ID
                                    :Int
                                    :String]}}
           (ex-data e)))))
