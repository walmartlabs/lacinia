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
    [clojure.test :refer [deftest is testing]]
    [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
    [com.walmartlabs.test-utils :refer [execute]]
    [com.walmartlabs.lacinia.schema :as schema]))

(def compiled-schema
  (let [test-schema {:interfaces
                     {:Node
                      {:fields {:id {:type '(non-null String)}}}}

                     :objects
                     {:Post
                      {:implements [:Node]
                       :fields     {:id       {:type '(non-null String)}
                                    :author   {:type    '(non-null :Author)
                                               :resolve (fn [_ _ _]
                                                          {:id "2000"})}
                                    :title    {:type    'String
                                               :resolve (fn [_ _ _]
                                                          "Hello, World!")}
                                    :comments {:type    '(list :Comment)
                                               :resolve (fn [_ _ _]
                                                          [{:id "3000"}])}}}

                      :Author
                      {:implements [:Node]
                       :fields     {:id         {:type '(non-null String)}
                                    :name       {:type    '(non-null String)
                                                 :resolve (fn [_ _ _]
                                                            "John Doe")}
                                    :alwaysNull {:type    'String
                                                 :resolve (fn [_ _ _]
                                                            nil)}
                                    :alwaysFail {:type    '(non-null String)
                                                 :resolve (fn [_ _ _]
                                                            (resolve-as nil {:message "This field can't be resolved."}))}}}

                      :Comment
                      {:implements [:Node]
                       :fields     {:id {:type '(non-null String)}}}}

                     :queries
                     {:node {:type    '(non-null :Node)
                             :args    {:id {:type '(non-null String)}}
                             :resolve (fn [ctx args v]
                                        (let [{:keys [episode]} args]
                                          (schema/tag-with-type {:id "1000"} :Post)))}}}]
    (schema/compile test-schema)))

(deftest deep-merge-on-error
  (is (= {:data   nil,
          :errors [{:message "This field can't be resolved.", :locations [{:line 4, :column 5}], :path [:node :author :alwaysFail]}]}
         (execute compiled-schema "
fragment PostFragment on Post {
  author {
    alwaysFail
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
          :errors [{:message "This field can't be resolved.", :locations [{:line 4, :column 5}], :path [:node :author :alwaysFail]}]}
         (execute compiled-schema "
fragment PostFragment on Post {
  author {
    alwaysFail
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
}")))

  (testing "when non-null field is resolved to nil, deep-merge should return nil"
    (is (= {:data   nil,
            :errors [{:message   "This field can't be resolved.",
                      :locations [{:line 13, :column 5}],
                      :path      [:node :author :alwaysFail]}]}
           (execute compiled-schema "
query MyQuery {
  node(id: \"1000\") {
    ... on Post {
      id
      ...PostFragment
    }
  }
}

fragment PostFragment on Post {
  author {
    alwaysFail
  }
  ...PostFragment2
}

fragment PostFragment2 on Post {
  author {
    name
  }
}
")))

    (is (= {:data   nil,
            :errors [{:message   "This field can't be resolved.",
                      :locations [{:line 14, :column 5}],
                      :path      [:node :author :alwaysFail]}]}
           (execute compiled-schema "
query MyQuery {
  node(id: \"1000\") {
    ... on Post {
      id
      ...PostFragment
    }
  }
}

fragment PostFragment on Post {
  ...PostFragment2
  author {
    alwaysFail
  }
}

fragment PostFragment2 on Post {
  author {
    name
  }
}
")))))
