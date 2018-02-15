(ns com.walmartlabs.lacinia.resolve
  "Complex results for field resolver functions."
  (:import (java.util.concurrent Executor)))

(def ^{:dynamic true
       :added "0.20.0"} *callback-executor*
  "If non-nil, then specifies a java.util.concurrent.Executor (typically, a thread pool of some form) used to invoke callbacks
  when ResolveResultPromises are delivered."
  nil)

(defprotocol ^{:added "0.24.0"} FieldResolver
  "Allows a Clojure record to operate as a field resolver."
  (resolve-value [this context args value]
    "The analog of a field resolver function, this method is passed the instance, and the standard
    context, field arguments, and container value, and returns a resolved value."))

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

(defn as-resolver-fn
  "Wraps a [[FieldResolver]] instance as a field resolver function.

  If the field-resolver provided is a function, it is returned unchanged."
  {:added "0.24.0"}
  [field-resolver]
  (if (fn? field-resolver)
    field-resolver
    (fn [context args value]
      (resolve-value field-resolver context args value))))

(defn wrap-resolver-result
  "Wraps a resolver function or ([[FieldResolver]] instance), passing the result through a wrapper function.

  The wrapper function is passed four values:  the context, arguments, and value
  as passed to the resolver, then the resolved value from the
  resolver.

  `wrap-resolver-result` understands resolver functions that return either a [[ResolverResult]]
  or a bare value, as well as functions that have decorated a value using [[with-error]] or
  [[with-context]].

  The wrapper-fn is passed the underlying value and must return a new value.
  The new value will be re-wrapped as necessary.

  The wrapped value may itself be a ResolverResult, and the
  value (either plain, or inside a ResolverResult) may also be decorated
  using `with-error` or `with-context`."
  {:added "0.23.0"}
  [resolver wrapper-fn]
  (let [resolver-fn (as-resolver-fn resolver)]
    ^ResolverResult
    (fn [context args value]
      (let [resolved-value (resolver-fn context args value)
            final-result (resolve-promise)
            deliver-final-result (fn [commands new-value]
                                   (deliver! final-result
                                             (if-not (seq commands)
                                               new-value
                                               (reduce #(replace-nested-value %2 %1)
                                                       new-value
                                                       commands))))
            invoke-wrapper (fn invoke-wrapper
                             ([value]
                              (invoke-wrapper nil value))
                             ([commands value]
                               ;; Wait, did someone just say "monad"?
                              (if (satisfies? ResolveCommand value)
                                (recur (cons value commands)
                                       (nested-value value))
                                (let [new-value (wrapper-fn context args value value)]
                                  (if (is-resolver-result? new-value)
                                    (on-deliver! new-value #(deliver-final-result commands %))
                                    (deliver-final-result commands new-value))))))]

        (if (is-resolver-result? resolved-value)
          (on-deliver! resolved-value invoke-wrapper)
          (invoke-wrapper resolved-value))

        final-result))))
