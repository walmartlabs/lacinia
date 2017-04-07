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
                    (on-deliver! r callback)))
    (is (= {:value :a-value
            :errors :some-errors
            :thread-name (thread-name)}
           (deref capture 100 nil)))))

(deftest promise-exposes-value-and-errors
  (let [p (resolve-promise)]
    (is (identical? p
                    (deliver! p :async-value :async-errors)))
    (is (= :async-value
           (resolved-value p)))
    (is (= :async-errors)
        (resolve-errors p))))

(deftest promise-when-single-argument-is-value
  (let [p (resolve-promise)]
    (is (identical? p
                    (deliver! p :async-value)))
    (is (= :async-value
           (resolved-value p)))
    (is (nil? (resolve-errors p)))))

(deftest promise-callback-is-invoked
  (let [p (resolve-promise)
        capture (atom nil)
        callback (fn [value errors]
                   (reset! capture {:value value :errors errors}))]
    (is (identical? p
                    (on-deliver! p callback)))
    (is (nil? @capture))

    (is (identical? p
                    (deliver! p :async-value :async-errors)))

    (is (= {:value :async-value
            :errors :async-errors}
           @capture))))

(deftest may-only-deliver-once
  (let [p (resolve-promise)]
    (deliver! p :first)
    (is (thrown? IllegalStateException
                 (deliver! p :second)))))

(deftest may-only-add-callback-once
  (let [p (resolve-promise)
        callback1 (fn [_ _])
        callback2 (fn [_ _])]
    (on-deliver! p callback1)
    (is (thrown? IllegalStateException
                 (on-deliver! p callback2)))))


