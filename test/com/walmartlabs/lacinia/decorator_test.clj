(ns com.walmartlabs.lacinia.decorator-test
  "Tests for field resolver decorators."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [compile-schema execute]]
    [clojure.string :as str]))


(defn ^:private decorator
  [object-name field-name f]
  (fn [context args value]
    (let [result (f context args value)]
      (if (string? result)
        (str/upper-case result)
        result))))


(def ^:private schema
  (compile-schema "decorator-schema.edn"
                  {:resolve-get-node (constantly {:name "node-name"
                                                  :description "node-description"})
                   :resolve-name (fn [_ _ value]
                                   (str "resolve<" (:name value) ">"))}
                  {:decorator decorator}))

(deftest decorator-is-applied
  (let [result (execute schema "{ get_node { name description }}")]
    ;; :description is via a default field resolver, it is not decorated
    ;; :name is via an explicit field resolver, its implementation is wrapped with the decorator
    (is (= {:data {:get_node {:description "node-description"
                              :name "RESOLVE<NODE-NAME>"}}}
           result))))
