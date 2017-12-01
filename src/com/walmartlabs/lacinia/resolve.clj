(ns com.walmartlabs.lacinia.resolve
  "Complex results for field resolver functions."
  (:import (java.util.concurrent Executor)))

(def ^{:dynamic true
       :added "0.20.0"} *callback-executor*
  "If non-nil, then specifies a java.util.concurrent.Executor (typically, a thread pool of some form) used to invoke callbacks
  when ResolveResultPromises are delivered."
  nil)

(defprotocol ^:no-doc ResolveCommand
  "Used to define special wrappers around resolved values, such as [[with-error]].

  This is not intended for use by applications, as the structure of the selection-context
  is not part of Lacinia's public API."
  (^:no-doc apply-command [this selection-context]
    "Applies changes to the selection context, which is returned.")

  (^:no-doc nested-value [this]
    "Returns the value wrapped by this command, which may be another command or a final result.")

  (^:no-doc replace-nested-value [this new-value]
    "Returns a new instance of the same command, but wrapped around a different nested value."))

(defn with-error
  "Decorates a value with an error map (or seq of error maps)."
  {:added "0.19.0"}
  [value error]
  (reify
    ResolveCommand

    (apply-command [_ selection-context]
      (update selection-context :errors conj error))

    (nested-value [_] value)

    (replace-nested-value [_ new-value]
      (with-error new-value error))))

(defn with-context
  "Decorates a value so that when nested fields (at any depth) are executed, the provided values will be in the context.

   The provided context-map is merged onto the application context."
  {:added "0.19.0"}
  [value context-map]
  (reify
    ResolveCommand

    (apply-command [_ selection-context]
      (update-in selection-context [:execution-context :context] merge context-map))

    (nested-value [_] value)

    (replace-nested-value [_ new-value]
      (with-context new-value context-map))))

(defprotocol ResolverResult
  "A special type returned from a field resolver that can contain a resolved value
  and/or errors."

    (on-deliver! [this callback]
    "Provides a callback that is invoked immediately after the ResolverResult is realized.
    The callback is passed the ResolverResult's value.

    `on-deliver!` should only be invoked once.
    It returns `this`.

    On a simple ResolverResult (not a ResolverResultPromise), the callback is invoked
    immediately.

    For a [[ResolverResultPromise]], the callback may be invoked on another thread.

    The callback is invoked for side-effects; its result is ignored."))

(defprotocol ResolverResultPromise
  "A specialization of ResolverResult that supports asynchronous delivery of the resolved value and errors."

  (deliver!
    [this value]
    [this value errors]
    "Invoked to realize the ResolverResult, triggering the callback to receive the value and errors.

    The callback is invoked in the current thread, unless [[*thread-pool*]] is non-nil, in which case
    the callback is invoked in a pooled thread.

    The two arguments version is simply a convienience around [[with-error]].

    Returns `this`."))

(defrecord ^:private ResolverResultImpl [resolved-value]

  ResolverResult

  (on-deliver! [this callback]
    (callback resolved-value)
    this))

(defn resolve-as
  "Invoked by field resolvers to wrap a simple return value as a ResolverResult.

  The two-arguments version is a convienience around using [[with-error]].

  This is an immediately realized ResolverResult.

  When [[on-deliver!]] is invoked, the provided callback is immediately invoked (in the same thread)."
  ([resolved-value]
   (->ResolverResultImpl resolved-value))
  ([resolved-value resolver-errors]
   (->ResolverResultImpl (with-error resolved-value resolver-errors))))

(defn resolve-promise
  "Returns a ResolverResultPromise.

   A value must be resolved and ultimately provided via [[deliver!]]."
  []
  (let [*result (promise)
        *callback (promise)]
    (reify
      ResolverResult

      ;; We could do a bit more locking to avoid a couple of race-condition edge cases, but this is mostly to sanity
      ;; check bad application code that simply gets the contract wrong.
      (on-deliver! [this callback]
        (cond
          ;; If the value arrives before the callback, invoke the callback immediately.
          (realized? *result)
          (callback @*result)

          (realized? *callback)
          (throw (IllegalStateException. "ResolverResultPromise callback may only be set once."))

          :else
          (deliver *callback callback))

        this)

      ResolverResultPromise

      (deliver! [this resolved-value]
        (when (realized? *result)
          (throw (IllegalStateException. "May only realize a ResolverResultPromise once.")))

        (deliver *result resolved-value)

        (when (realized? *callback)
          (let [callback @*callback]
            (if-some [^Executor executor *callback-executor*]
              (.execute executor (bound-fn [] (callback resolved-value)))
              (callback resolved-value))))

        this)

      (deliver! [this resolved-value error]
        (deliver! this (with-error resolved-value error))))))

(defn ^:no-doc combine-results
  "Given a left and a right ResolverResult, returns a new ResolverResult that combines
  the realized values using the provided function."
  [f left-result right-result]
  (let [combined-result (resolve-promise)]
    (on-deliver! left-result
                 (fn receive-left-value [left-value]
                   (on-deliver! right-result
                                (fn receive-right-value [right-value]
                                  (deliver! combined-result (f left-value right-value))))))
    combined-result))


(defn is-resolver-result?
  "Is the provided value actually a ResolverResult?"
  {:added "0.23.0"}
  [value]
  (when value
    ;; This is a little bit of optimization; satisfies? can
    ;; be a bit expensive.
    (or (instance? ResolverResultImpl value)
        (satisfies? ResolverResult value))))

(defn ^:private deliver-final-result
  [result-promise commands new-value]
  (deliver! result-promise
            (if-not (seq commands)
              new-value
              (reduce #(replace-nested-value %2 %1)
                      new-value
                      commands))))

(defn ^:private make-invoke-transform
  [result-promise transform-fn]
  (fn invoke-transform
    ([value]
     (invoke-transform nil value))
    ([commands value]
     (if (satisfies? ResolveCommand value)
       (recur (cons value commands)
              (nested-value value))
       (let [new-value (transform-fn value)]
         (if (is-resolver-result? new-value)
           (on-deliver! new-value #(deliver-final-result result-promise commands %))
           (deliver-final-result result-promise commands new-value)))))))

(defn chain*
  "Threads a ResolverResult through a transform function.

  The resolved value (stripped of any wrappers) is passed to the transform function.

  The transform function can return a new value, a wrapped value, or
  a ResolverResult.

  Returns a new ResolverResult (a promise)."
  {:added "0.24.0"}
  [resolver-result transform-fn]
  (let [combined-result (resolve-promise)
        invoke-transform (make-invoke-transform combined-result transform-fn)]
    (on-deliver! resolver-result #(invoke-transform %))
    combined-result))

(defmacro chain
  "Chains a resolver result through a series of expressions, using [[chain*]].

  Each expression is passed the resolved value of the previous ResolverResult
  as `$`.  As with [[chain*]], the expression may return a simple value, a wrapped value,
  or a ResolverResult."
  [resolver-result & exprs]
  {:added "0.24.0"}
  `(-> ~resolver-result
       ~@(map (fn [e] (list `chain* `(fn [~'$] ~e))) exprs)))

(defn wrap-resolver-result
  "Wraps a resolver function, passing the result through a wrapper function.

  The wrapper function is passed four values:  the context, arguments, and value
  as passed to a resolver function, then the resolved value from the
  resolver.

  `wrap-resolver-result` understands resolver functions that return either a [[ResolverResult]]
  or a bare value, as well as functions that have decorated a value using [[with-error]] or
  [[with-context]].
  The wrapper-fn is passed the underlying value and must return a new value.
  The new value will be re-wrapped as necessary.

  The wrapped value may itself be a ResolverResult, and the
  value (either plain, or inside a ResolverResult) may also be decorated
  with `with-error` or `with-context`."
  {:added "0.23.0"}
  [resolver-fn wrapper-fn]
  ^ResolverResult
  (fn [context args value]
    (let [resolved-value (resolver-fn context args value)
          transform-fn (fn [resolved-value]
                         (wrapper-fn context args value resolved-value))
          final-result (resolve-promise)
          invoke-transform (make-invoke-transform final-result transform-fn)]

      (if (is-resolver-result? resolved-value)
        (on-deliver! resolved-value invoke-transform)
        (invoke-transform resolved-value))

      final-result)))
