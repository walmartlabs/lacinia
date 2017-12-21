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

(defn ^:private inc-wrapper
  [_ _ _ value]
  (inc value))

(defn ^:private as-promise [resolver-result]
  (let [p (promise)]
    (r/on-deliver! resolver-result #(deliver p %))
    p))

(defn ^:private apply-resolve-command
  [selection-context value]
  (if (satisfies? r/ResolveCommand value)
    (apply-resolve-command
      (r/apply-command value selection-context)
      (r/nested-value value))
    [selection-context value]))

(deftest wrapper-invoked-for-raw-value
  (let [resolver-fn (constantly 100)
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))]
    (is (= 101 @*result))))

(deftest wrapper-invoked-with-value-unpacked-from-resolver-result
  (let [resolver-fn (constantly (r/resolve-as 200))
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))]
    (is (= 201 @*result))))

(deftest restores-commands-around-wrapped-value
  (let [resolver-fn (constantly (-> 300
                                    (r/with-context {:gnip :gnop})
                                    (r/with-error :fail-1)
                                    (r/with-error :fail-2)))
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))
        [sc final-value] (apply-resolve-command {} @*result)]
    (is (= 301 final-value))
    (is (= {:errors [:fail-1 :fail-2]                       ; check order of application
            :execution-context {:context {:gnip :gnop}}}
           sc))))

(deftest wrapped-value-may-itself-be-resolver-result
  (let [resolver-promise (r/resolve-promise)
        resolver-fn (constantly resolver-promise)
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))]
    (r/deliver! resolver-promise 500)
    (is (= 501 (deref *result 100 ::no-value)))))

(deftest chain
  (let [initial-promise (r/resolve-promise)
        p (-> initial-promise
              (r/chain
                (+ $ 10)
                (r/resolve-as (inc $))
                (* $ 50))
              as-promise)]
    (is (not (realized? p)))

    (r/deliver! initial-promise 17)

    (is (realized? p))

    (is (= 1400 @p))))
