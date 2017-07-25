(ns com.walmartlabs.lacinia.resolver-result-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :as r])
  (:import (java.util.concurrent Executor)))

(deftest resolve-as-returns-resolver-result
  (is (satisfies? r/ResolverResult (r/resolve-as nil))))

(defn ^:private thread-name
  []
  (.getName (Thread/currentThread)))

(deftest callback-is-invoked
  (let [capture (promise)
        callback (fn [value]
                   (deliver capture {:value value
                                     :thread-name (thread-name)}))
        r (r/resolve-as :a-value)]
    (is (identical? r
                    (r/on-deliver! r callback)))
    (is (= {:value :a-value
            :thread-name (thread-name)}
           (deref capture 100 nil)))))

(deftest promise-callback-is-invoked
  (let [p (r/resolve-promise)
        capture (atom nil)
        callback (fn [value]
                   (reset! capture {:value value}))]
    (is (identical? p
                    (r/on-deliver! p callback)))
    (is (nil? @capture))

    (is (identical? p
                    (r/deliver! p :async-value)))

    (is (= {:value :async-value}
           @capture))))

(deftest may-only-deliver-once
  (let [p (r/resolve-promise)]
    (r/deliver! p :first)
    (is (thrown? IllegalStateException
                 (r/deliver! p :second)))))

(deftest may-only-add-callback-once
  (let [p (r/resolve-promise)
        callback1 (fn [_])
        callback2 (fn [_])]
    (r/on-deliver! p callback1)
    (is (thrown? IllegalStateException
                 (r/on-deliver! p callback2)))))

(deftest will-invoke-callback-using-provided-executor
  (let [*callback-values (atom [])
        *execute-count (atom 0)
        resolved-value (gensym)
        callback (fn [value]
                   (swap! *callback-values conj value))
        executor (reify Executor
                   (execute [_ runnable]
                     (swap! *execute-count inc)
                     (.run runnable)))
        resolve-result (r/resolve-promise)]
    (r/on-deliver! resolve-result callback)

    (binding [r/*callback-executor* executor]
      (r/deliver! resolve-result resolved-value))

    (is (= 1 @*execute-count))
    (is (= [resolved-value] @*callback-values))))
