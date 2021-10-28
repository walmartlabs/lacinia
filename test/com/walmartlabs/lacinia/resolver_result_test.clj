; Copyright (c) 2017-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns com.walmartlabs.lacinia.resolver-result-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.resolve :as r :refer [FieldResolver]]
    [com.walmartlabs.lacinia.select-utils :as su]
    [com.walmartlabs.lacinia.resolve-utils :refer [aggregate-results]]
    [com.walmartlabs.lacinia.resolve :as resolve])
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

(defn ^:private as-promise
  [resolver-result]
  (let [p (promise)]
    (r/on-deliver! resolver-result p)
    p))

(defn ^:private apply-wrapped-values
  [selection-context selection value]
  (if (su/is-wrapped-value? value)
    (apply-wrapped-values
      (su/apply-wrapped-value selection-context selection value)
      selection
      (:value value))
    [selection-context value]))

(deftest wrapper-invoked-for-raw-value
  (let [resolver-fn (constantly 100)
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))]
    (is (= 101 @*result))))

(deftest supports-field-resolvers
  (let [resolver-fn (reify FieldResolver
                      (resolve-value [_ _ _ _] 97))
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))]
    (is (= 98 @*result))))

(deftest wrapper-invoked-with-value-unpacked-from-resolver-result
  (let [resolver-fn (constantly (r/resolve-as 200))
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))]
    (is (= 201 @*result))))

(deftest restores-commands-around-wrapped-value
  (let [resolver-fn (constantly (-> 300
                                    (r/with-context {:gnip :gnop})
                                    (r/with-error {:message "fail 1"})
                                    (r/with-error {:message "fail 2"})
                                    (r/with-warning {:message "warn 1"})
                                    (r/with-warning {:message "warn 2"})
                                    (r/with-extensions assoc-in [:fie :fie :foe] :fum)))
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))
        *extensions (atom {})
        *errors (atom [])
        *warnings (atom [])
        context {:*extensions *extensions
                 :*errors *errors
                 :*warnings *warnings}
        [context' final-value] (apply-wrapped-values context nil @*result)]
    (is (= 301 final-value))
    (is (= {:fie {:fie {:foe :fum}}}
           @*extensions))
    (is (= [{:locations [nil]
             :message "fail 2"
             :path nil}
            {:locations [nil]
             :message "fail 1"
             :path nil}]
          @*errors))
     (is (= [{:locations [nil]
              :message "warn 2"
              :path nil}
             {:locations [nil]
              :message "warn 1"
              :path nil}]
           @*warnings))
    (is (= {:*errors *errors                 ; check order of application
            :*warnings *warnings
            :context {:gnip :gnop}
            :*extensions *extensions}
           context'))))

(deftest wrapped-value-may-itself-be-resolver-result
  (let [resolver-promise (r/resolve-promise)
        resolver-fn (constantly resolver-promise)
        wrapped (r/wrap-resolver-result resolver-fn inc-wrapper)
        *result (as-promise (wrapped nil nil nil))]
    (r/deliver! resolver-promise 500)
    (is (= 501 (deref *result 100 ::no-value)))))

(deftest resolver-result-promise-has-to-string
  (let [p (r/resolve-promise)]
    (is (re-matches #"ResolverResultPromise\[\d+\]" (str p)))

    (r/on-deliver! p identity)

    (is (re-matches #"ResolverResultPromise\[\d+\, callback]" (str p)))

    (r/deliver! p :anything)

    (is (re-matches #"ResolverResultPromise\[\d+\, callback, resolved]" (str p)))))

(deftest aggregate-with-promises
  ;; The test schemas don't fully exercise aggregate-results because the aggregate data
  ;; is always already resolved.  This checks behavior when the ResolverResults are RRPromises
  ;; that aren't yet realized.
  (let [values (range 20 40)
        n (count values)
        *final (promise)
        results (repeatedly n resolve/resolve-promise)
        agg (aggregate-results results)]

    (resolve/on-deliver! agg *final)

    (is (not (realized? *final)))

    ;; Do it for side-effects

    (doall
      (map #(resolve/deliver! %1 %2)
           results
           values))

    (is (= values
           (deref *final 100 ::timeout)))))

(deftest aggregate-with-transform
  (let [values [:moe :larry :curly :moe :curly :larry :shemp :moe]
        n (count values)
        *final (promise)
        results (repeatedly n resolve/resolve-promise)
        agg (aggregate-results results frequencies)]

    (resolve/on-deliver! agg *final)

    (is (not (realized? *final)))

    ;; Do it for side-effects

    (doall
      (map #(resolve/deliver! %1 %2)
           results
           values))

    (is (= {:curly 2
            :larry 2
            :moe 3
            :shemp 1}
           (deref *final 100 ::timeout)))))

(deftest aggreate-with-transform-mixed
  (let [*final (promise)
        rp (resolve/resolve-promise)
        results [(resolve/resolve-as :moe)
                 (resolve/resolve-as :larry)
                 rp]
        agg (aggregate-results results frequencies)]

    (resolve/on-deliver! agg *final)

    (is (not (realized? *final)))

    (resolve/deliver! rp :moe)

    (is (= {:moe 2 :larry 1}
           (deref *final 100 ::timeout)))))

(deftest aggregate-solo-with-transform
  (let [*final (promise)
        rp (resolve/resolve-promise)
        results [rp]
        agg (aggregate-results results frequencies)]

    (resolve/on-deliver! agg *final)

    (is (not (realized? *final)))

    (resolve/deliver! rp :moe)

    (is (= {:moe 1}
           (deref *final 100 ::timeout)))))

(deftest aggregate-solo-resolved-with-transform
  (let [*final (promise)
        results [(resolve/resolve-as :curly)]
        agg (aggregate-results results frequencies)]

    (resolve/on-deliver! agg *final)

    (is (realized? *final))

    (is (= {:curly 1}
           (deref *final 100 ::timeout)))))

(deftest aggregate-empty-with-xform
  (let [*final (promise)
        agg (aggregate-results [] frequencies)]
    (resolve/on-deliver! agg *final)
    (is (= {}
           (deref *final 100 ::timeout)))))

(defrecord ^:private NonStandardRR []

  r/ResolverResult
  (on-deliver! [_ _]))

(def recogonizes-resolver-result
  ;; Test the very optimized version of is-resolver-result?
  (let [reified (reify
             r/ResolverResult
             (on-deliver! [_ _]))
        record (->NonStandardRR)]
    (is (r/is-resolver-result? reified))
    (is (r/is-resolver-result? record))))

