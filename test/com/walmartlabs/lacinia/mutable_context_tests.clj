(ns com.walmartlabs.lacinia.mutable-context-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.test-utils :refer [compile-schema execute]]))

(def ^:private schema (compile-schema "mutable-context-schema.edn"
                                      {:root (fn [_ args _]
                                               (cond-> {:container {:id "0001"
                                                                    :leaf "DEFAULT"}}
                                                 (:trigger args) (resolve/with-context {::leaf-value "OVERRIDE"})))
                                       :leaf (fn [context _ container]
                                               (or (::leaf-value context)
                                                   (:leaf container)))}))

(deftest resolver-may-modify-nested-context
  (is (= {:data {:disabled {:container {:id "0001"
                                        :leaf "DEFAULT"}}
                 :enabled {:container {:id "0001"
                                       :leaf "OVERRIDE"}}}}
         (execute schema
                  "{ enabled: root(trigger: true) { container { id leaf }}
                     disabled: root { container { id leaf }}}"))))
