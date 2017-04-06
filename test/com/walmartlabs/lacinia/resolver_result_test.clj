(ns com.walmartlabs.lacinia.resolver-result-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :refer :all]))

(deftest resolve-as-returns-resolver-result
  (is (satisfies? ResolverResult (resolve-as nil))))

(deftest resolve-as-single-arg-is-value
  (let [r (resolve-as :test-value)]
    (is (= :test-value (resolved-value r)))
    (is (nil? (resolve-errors r)))))

(deftest resolve-as-two-args-is-value-and-errors
  (let [r (resolve-as :test-value :an-error)]
    (is (= :test-value (resolved-value r)))
    (is (= :an-error (resolve-errors r)))))

(defn ^:private thread-name
  []
  (.getName (Thread/currentThread)))

(deftest callback-is-invoked-with-value-and-errors
  (let [capture (promise)
        callback (fn [value errors]
                   (deliver capture {:value value
                                     :errors errors
                                     :thread-name (thread-name)}))
        r (resolve-as :a-value :some-errors)]
    (is (identical? r
                    (when-ready! r callback)))
    (is (= {:value :a-value
            :errors :some-errors
            :thread-name (thread-name)}
           (deref capture 100 nil)))))

(deftest deferred-resolve-exposes-value-and-errors
  (let [d (deferred-resolve)]
    (is (identical? d
                    (resolve-async! d :async-value :async-errors)))
    (is (= :async-value
           (resolved-value d)))
    (is (= :async-errors)
        (resolve-errors d))))

(deftest deferred-resolve-single-argument-is-value
  (let [d (deferred-resolve)]
    (is (identical? d
                    (resolve-async! d :async-value)))
    (is (= :async-value
           (resolved-value d)))
    (is (nil? (resolve-errors d)))))

(deftest deferred-callback-is-invoked
  (let [d (deferred-resolve)
        capture (atom nil)
        callback (fn [value errors]
                   (reset! capture {:value value :errors errors}))]
    (is (identical? d
                    (when-ready! d callback)))
    (is (nil? @capture))

    (is (identical? d
                    (resolve-async! d :async-value :async-errors)))

    (is (= {:value :async-value
            :errors :async-errors}
           @capture))))

(deftest may-only-realize-once
  (let [d (deferred-resolve)]
    (resolve-async! d :first)
    (is (thrown? IllegalStateException
                 (resolve-async! d :second)))))

(deftest may-only-add-callback-once
  (let [d (deferred-resolve)
        callback1 (fn [_ _])
        callback2 (fn [_ _])]
    (when-ready! d callback1)
    (is (thrown? IllegalStateException
                 (when-ready! d callback2)))))

(deftest may-only-add-callback-before-realized
  (let [d (deferred-resolve)
        callback (fn [_ _])]
    (resolve-async! d :async-value)
    (is (thrown? IllegalStateException
                 (when-ready! d callback)))))


