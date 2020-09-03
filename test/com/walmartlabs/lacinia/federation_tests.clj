(ns com.walmartlabs.lacinia.federation-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.parser.schema :refer [parse-schema]]
    [com.walmartlabs.test-utils :refer [execute]]
    [com.walmartlabs.lacinia.schema :as schema]))

(deftest essentials
  (let [sdl (slurp "dev-resources/simple-federation.sdl")
        schema (-> (parse-schema sdl {:federation {:entity-resolvers {:User (fn [_ _ _] nil)}}})
                   schema/compile)]
    (is (= {:data {:_service {:sdl sdl}}}
           (execute schema
                    "{ _service { sdl }}")))))