;; Copyright (c) 2017-present Walmart, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License")
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns com.walmartlabs.lacinia.executor-test
  "Tests for errors and exceptions inside field resolvers, and for the exception converter."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
    [com.walmartlabs.test-utils :refer [execute]]
    [com.walmartlabs.lacinia.schema :as schema]))

(deftest deep-merge-on-error
  (let [test-schema {:interfaces
                     {:Node
                      {:fields {:id {:type '(non-null String)}}}}

                     :objects
                     {:Post
                      {:implements [:Node]
                       :fields     {:id     {:type '(non-null String)}
                                    :author {:type    '(non-null :Author)
                                             :resolve (fn [_ _ _]
                                                        {:id "2000"})}
                                    :title  {:type    'String
                                             :resolve (fn [_ _ _]
                                                        "Hello, World!")}}}

                      :Author
                      {:implements [:Node]
                       :fields     {:id     {:type '(non-null String)}
                                    :name   {:type    '(non-null String)
                                             :resolve (fn [_ _ _]
                                                        "John Doe")}
                                    :absurd {:type    '(non-null String)
                                             :resolve (fn [_ _ _]
                                                        (resolve-as nil {:message "This field can't be resolved."}))}}}}

                     :queries
                     {:node {:type    '(non-null :Node)
                             :args    {:id {:type '(non-null String)}}
                             :resolve (fn [ctx args v]
                                        (let [{:keys [episode]} args]
                                          (schema/tag-with-type {:id "1000"} :Post)))}}}
        compiled-schema (schema/compile test-schema)]

    (is (= {:data   nil,
            :errors [{:message "This field can't be resolved.", :locations [{:line 4, :column 5}], :path [:node :author :absurd]}]}
           (execute compiled-schema "
fragment PostFragment on Post {
  author {
    absurd
  }
}
query MyQuery {
  node(id: \"1000\") {
    ... on Post {
      ...PostFragment
      author {
        name
      }
    }
    id
  }
}")))

    (is (= {:data   nil,
            :errors [{:message "This field can't be resolved.", :locations [{:line 4, :column 5}], :path [:node :author :absurd]}]}
           (execute compiled-schema "
fragment PostFragment on Post {
  author {
    absurd
  }
}
query MyQuery {
  node(id: \"1000\") {
    ... on Post {
      author {
        name
      }
      ...PostFragment
    }
    id
  }
}")))))
