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

(ns com.walmartlabs.lacinia.resolver-errors-test
  "Tests for errors and exceptions inside field resolvers, and for the exception converter."
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.walmartlabs.lacinia.resolve :refer [resolve-as with-error]]
   [com.walmartlabs.test-utils :refer [execute compile-schema] :as utils]
   [com.walmartlabs.lacinia.schema :as schema])
  (:import (clojure.lang ExceptionInfo)
           (java.awt Color)))

(defn ^:private resolve-exception
  [_ _ _]
  (throw (ex-info "Fail!" {:reason :testing})))

(def ^:private resolver-map
  {:single-error (fn [_ _ _]
                   (resolve-as nil {:message "Exception in error_field resolver."}))
   :exception resolve-exception
   :with-extensions (fn [_ _ _]
                      ;; Previously, the :extensions here would be nested within a new :extensions map,
                      ;; want to show that it is merged into the top-level extensions instead.
                      (resolve-as nil {:message "Exception with extensions."
                                       :top-level :data
                                       :extensions {:nested :data}}))
   :color (fn [_ _ _]
            ;; We could add a serializer that converts this to :BLUE, but we haven't.
            Color/BLUE)
   :multiple-errors (fn [_ _ _]
                      (reduce #(with-error %1 %2)
                        "Value"
                        [{:message "1" :other-key 100}
                                                {:message "2"}
                                                {:message "3"}
                                                {:message "4"}]))
   :resolve-root (fn [_ _ _] {})})

(def default-schema
  (utils/compile-schema "field-resolver-errors.edn"
                        resolver-map))

(deftest exception-inside-resolver
  (let [e (is (thrown? ExceptionInfo
                       (execute default-schema
                                "{ root { exception (range: 5) }}")))
        cause (ex-cause e)]
    ;; Specifically, despite the recursion, there isn't an exception for
    ;; QueryRoot/root, just for MyObject/exception.
    (is (= "Exception in resolver for `MyObject/exception': Fail!" (ex-message e)))
    (is (= {:field-name :MyObject/exception
            :location {:column 10 :line 1}
            :arguments {:range 5}
            :path [:root :exception]}
           (ex-data e)))
    (is (= "Fail!" (ex-message cause)))
    (is (= {:reason :testing} (ex-data cause)))
    (is (= nil (ex-cause cause)))))

(deftest exception-during-selection
  (let [e (is (thrown? ExceptionInfo
                       (execute default-schema
                                "{ root { color }}")))
        cause (ex-cause e)]
    (is (= "Exception processing resolved value for `MyObject/color': Can't convert value to keyword."
           (ex-message e)))
    (is (= {:arguments nil
            :field-name :MyObject/color
            :location {:column 10 :line 1}
            :path [:root :color]}
           (ex-data e)))
    (is (= "Can't convert value to keyword." (ex-message cause)))
    (is (= {:value Color/BLUE} (ex-data cause)))
    (is (= nil (ex-cause cause)))))

(deftest field-with-single-error
  (is (= {:data {:root {:error_field nil}}
          :errors [{:locations [{:column 10
                                 :line 1}]
                    :message "Exception in error_field resolver."
                    :path [:root :error_field]}]}
         (execute default-schema
                  "{ root { error_field }}"))))

(deftest field-with-multiple-errors
  (is (= [{:locations [{:column 10
                        :line 1}]
           :message "1"
           :path [:root :multiple_errors_field]
           :extensions {:other-key 100}}
          {:locations [{:column 10
                        :line 1}]
           :message "2"
           :path [:root :multiple_errors_field]}
          {:locations [{:column 10
                        :line 1}]
           :message "3"
           :path [:root :multiple_errors_field]}
          {:locations [{:column 10
                        :line 1}]
           :message "4"
           :path [:root :multiple_errors_field]}]
         (->> (execute default-schema "{ root { multiple_errors_field }}")
              :errors
              (sort-by :message)))))

(deftest extensions-are-merged
  (is (= {:data {:root {:with_extensions nil}}
          :errors [{:extensions {:nested :data
                                 :top-level :data}
                    :locations [{:column 10
                                 :line 1}]
                    :message "Exception with extensions."
                    :path [:root
                           :with_extensions]}]}
         (execute default-schema "{ root { with_extensions } }"))))

(deftest errors-are-propagated
  (testing "errors are propagated from sub-selectors even when no data is returned"
    (let [container-data {"empty-container" {:contents []}
                          "full-container" {:contents [{:name "Book"}
                                                       {:name "Picture"}]}}
          schema (schema/compile {:objects
                                  {:item
                                   {:fields {:name {:type 'String}}}
                                   :container
                                   {:fields {:id {:type 'String}
                                             :contents {:type '(list :item)
                                                        :resolve (fn [ctx args v]
                                                                   (resolve-as
                                                                     (:contents v)
                                                                     {:message "Some error"}))}}}}
                                  :queries
                                  {:container {:type :container
                                               :args {:id {:type 'String}}
                                               :resolve (fn [ctx args v]
                                                          (get container-data (:id args)))}}})]
      (testing "when the sub-selector returns data"
        (let [q "query foo { container(id:\"full-container\") { contents { name } } }"]
          (is (= {:data {:container {:contents [{:name "Book"}
                                                {:name "Picture"}]}}
                  :errors [{:locations [{:column 46
                                         :line 1}]
                            :message "Some error"
                            :path [:container :contents]}]}
                 (execute schema q)))))
      (testing "when the sub-selector returns an empty collection"
        (let [q "query foo { container(id:\"empty-container\") { contents { name } } }"]
          ;; Event though the container contents are empty, the field resolver is still invoked,
          ;; and the attached error is still processed.
          (is (= {:data {:container {:contents []}}
                  :errors [{:locations [{:column 47
                                         :line 1}]
                            :message "Some error"
                            :path [:container :contents]}]}
                 (execute schema q))))))))

;; When the non-null selector sees an invalid null, it normally identifies the field BUT
;; according to the spec, there should only be one error per field, so if there's already any errors
;; (perhaps created by the application resolver code) then, yes, skip the rest of the selector pipeline but
;; don't record a new error.
(deftest non-chatty-library-failures
  (let [schema (compile-schema "non-chatty-library-failures.edn"
                               {:hello (fn [_ _ _]
                                         (resolve-as nil {:message "Error processing request"}))})]
    (is (= {:data nil
            :errors [{:locations [{:column 3
                                   :line 1}]
                      :message "Error processing request"
                      :path [:hello]}]}
           (execute schema "{ hello }")))))