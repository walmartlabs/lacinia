(ns com.walmartlabs.lacinia.resolver-result-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :as r]))

(deftest resolve-as-returns-resolver-result
  (is (satisfies? r/ResolverResult (r/resolve-as nil))))

(defn ^:private thread-name
  []
  (.getName (Thread/currentThread)))

(deftest callback-is-invoked-with-value-and-errors
  (let [capture (promise)
        callback (fn [value errors]
                   (deliver capture {:value value
                                     :errors errors
                                     :thread-name (thread-name)}))
        r (r/resolve-as :a-value :some-errors)]
    (is (identical? r
                    (r/on-deliver! r callback)))
    (is (= {:value :a-value
            :errors :some-errors
            :thread-name (thread-name)}
           (deref capture 100 nil)))))

(deftest promise-callback-is-invoked
  (let [p (r/resolve-promise)
        capture (atom nil)
        callback (fn [value errors]
                   (reset! capture {:value value :errors errors}))]
    (is (identical? p
                    (r/on-deliver! p callback)))
    (is (nil? @capture))

    (is (identical? p
                    (r/deliver! p :async-value :async-errors)))

    (is (= {:value :async-value
            :errors :async-errors}
           @capture))))

(deftest may-only-deliver-once
  (let [p (r/resolve-promise)]
    (r/deliver! p :first)
    (is (thrown? IllegalStateException
                 (r/deliver! p :second)))))

(deftest may-only-add-callback-once
  (let [p (r/resolve-promise)
        callback1 (fn [_ _])
        callback2 (fn [_ _])]
    (r/on-deliver! p callback1)
    (is (thrown? IllegalStateException
                 (r/on-deliver! p callback2)))))


