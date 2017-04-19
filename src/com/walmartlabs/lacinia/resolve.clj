(ns com.walmartlabs.lacinia.resolve
  "Complex results for field resolver functions.")

(defprotocol ResolverResult
  "A special type returned from a field resolver that can contain a resolved value
  and/or errors."

    (on-deliver! [this callback]
    "Provides a callback that is invoked immediately after the ResolverResult is realized.
    The callback is passed the ResolverResult's value and errors.

    `on-deliver!` should only be invoked once.
    It returns the ResolverResult.

    On a simple ResolverResult (not a ResolverResultPromise), the callback is invoked
    immediately.

    The callback is invoked for side-effects; its result is ignored."))

(defprotocol ResolverResultPromise
  "A specialization of ResolverResult that supports asynchronous delivery of the resolved value and errors."

  (deliver!
    [this value]
    [this value errors]
    "Invoked to realize the ResolverResult, triggering the callback to receive the value and errors.

    Returns the deferred resolver result."))

(defrecord ^:private ResolverResultImpl [resolved-value resolve-errors]

  ResolverResult

  (on-deliver! [this callback]
    (callback resolved-value resolve-errors)
    this))

(defn resolve-as
  "Invoked by field resolvers to wrap a simple return value as a ResolverResult.

  This is an immediately realized ResolverResult."
  ([resolved-value]
   (resolve-as resolved-value nil))
  ([resolved-value resolver-errors]
   (->ResolverResultImpl resolved-value resolver-errors)))

(defn resolve-promise
  "Returns a ResolverResultPromise.

   A value must be resolved and ultimately provided via [[deliver!]]."
  []
  (let [realized-result (promise)
        callback-promise (promise)]
    (reify
      ResolverResult

      ;; We could do a bit more locking to avoid a couple of race-condition edge cases, but this is mostly to sanity
      ;; check bad application code that simply gets the contract wrong.
      (on-deliver! [this callback]
        (cond
          (realized? realized-result)
          (on-deliver! @realized-result callback)

          (realized? callback-promise)
          (throw (IllegalStateException. "ResolverResultPromise callback may only be set once."))

          :else
          (deliver callback-promise callback))

        this)

      ResolverResultPromise

      (deliver! [this resolved-value]
        (deliver! this resolved-value nil))

      (deliver! [this resolved-value errors]
        (when (realized? realized-result)
          (throw (IllegalStateException. "May only realize a ResolverResultPromise once.")))

        ;; Need to capture the results if they arrive before the call to on-deliver!
        (deliver realized-result (resolve-as resolved-value errors))

        (when (realized? callback-promise)
          (@callback-promise resolved-value errors))

        this))))
