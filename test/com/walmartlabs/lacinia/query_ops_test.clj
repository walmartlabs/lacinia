(ns com.walmartlabs.lacinia.query-ops-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [compile-schema execute]]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia.schema :as schema]))

(def default-schema (schema/compile test-schema))

(deftest may-identify-op-when-single-op
  (is (= {:data {:human {:name "Han Solo"}}}
         (execute default-schema
                  "query solo($id: String!) { human(id: $id) { name } }"
                  {:id "1002"}
                  nil
                  {:operation-name "solo"}))))
