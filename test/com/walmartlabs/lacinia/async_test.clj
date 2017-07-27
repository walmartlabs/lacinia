(ns com.walmartlabs.lacinia.async-test
  "Tests for field resolvers that return deferred ResolverResults."
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.walmartlabs.lacinia :as lacinia]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :refer [simplify]]))

(def execution-order (atom []))

(defn delayed-result
  [wait-for exec-name value]
  (let [result (resolve/resolve-promise)
        body (fn []
               (when (and wait-for
                          (not= wait-for :any))
                 (loop [attempt 0]
                   (when (> attempt 150)
                     (throw (IllegalStateException.
                              (format "Delayed result failed to see expected execution after ~ 3 seconds. Waiting for %s, saw %s."
                                      (pr-str wait-for)
                                      (pr-str @execution-order)))))
                   (when-not (-> @execution-order set (contains? wait-for))
                     (Thread/sleep 20)
                     (recur (inc attempt)))))
               (swap! execution-order conj (keyword exec-name))
               (resolve/deliver! result value))]
    (doto
      (Thread. ^Runnable body (str "test-thread-" exec-name))
      .start)
    result))

(use-fixtures :each
  (fn [f]
    (try
      (f)
      (finally
        (swap! execution-order empty)))))

(defn node-resolver
  [_ args _]
  (let [wait (:wait args)]
    (if (some? wait)
      (delayed-result (keyword wait) (:name args) args)
      args)))

(def compiled-schema
  (-> '{:objects
        {:node
         {:fields {:name {:type String}}}}
        :queries
        {:node {:type :node
                :args {:wait {:type String}
                       :name {:type (non-null String)}}
                :resolve :node}}

        :mutations
        {:mnode {:type :node
                 :args {:wait {:type String}
                        :name {:type (non-null String)}}
                 :resolve :node}}}
      (util/attach-resolvers {:node node-resolver})
      schema/compile))

(defn q [query]
  (lacinia/execute compiled-schema query nil nil))

(deftest queries-execute-in-parallel
  (let [result
        (q "{
        n1: node(wait: \"n5\", name: \"n1\") {  name }
        n2: node(wait: \"any\", name: \"n2\") { name}
        n3: node(name: \"n3\") { name }
        n4: node(wait: \"n1\", name: \"n4\") { name }
        n5: node(wait: \"n2\", name: \"n5\") { name }
        }")
        actual-order @execution-order]
    ;; This shows that all the requested data did arrive, but potentially obscures
    ;; the order.
    (is (= {:data {:n1 {:name "n1"}
                   :n2 {:name "n2"}
                   :n3 {:name "n3"}
                   :n4 {:name "n4"}
                   :n5 {:name "n5"}}}
           (simplify result)))
    ;; Even though we have proof that the field resolvers ran in a different order,
    ;; this shows that the results from each FR was added to the result map in
    ;; the user-requested order.
    (is (= [:n1 :n2 :n3 :n4 :n5]
           (-> result :data keys)))
    ;; :n3 doesn't appear here because it returns immediately
    (is (= [:n2 :n5 :n1 :n4] actual-order))))

(deftest mutations-execute-serially
  ;; This test is of questionable utility.
  ;; Originally the threads operated with sleeps, but that was unpredictable
  ;; on the CI server for some reason.
  (let [result
        (q "mutation {
        n1: mnode(wait: \"any\",  name: \"n1\") {  name }
        n2: mnode(wait: \"any\" name: \"n2\") { name }
        n3: mnode( name: \"n3\") { name }
        n4: mnode(wait: \"any\",  name: \"n4\") { name }
        n5: mnode(wait: \"any\", name: \"n5\") { name }
         }")
        actual-order @execution-order]
    ;; This shows that all the requested data did arrive, but potentially obscures
    ;; the order.
    (is (= {:data {:n1 {:name "n1"}
                   :n2 {:name "n2"}
                   :n3 {:name "n3"}
                   :n4 {:name "n4"}
                   :n5 {:name "n5"}}}
           (simplify result)))
    (is (= [:n1 :n2 :n3 :n4 :n5]
           (-> result :data keys)))
    ;; :n3 doesn't appear here because it returns immediately
    (is (= [:n1 :n2 :n4 :n5] actual-order))))
