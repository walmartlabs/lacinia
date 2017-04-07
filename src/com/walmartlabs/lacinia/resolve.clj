(ns com.walmartlabs.lacinia.resolve
  "Complex results for field resolver functions.")

;; This can be extended in the future to encompass a value that may be resolved
;; asynchronously.
(defprotocol ResolverResult
  "A special type returned from a field resolver that can contain a resolved value
  and/or errors."

  (resolved-value [this]
    "Returns the value resolved by the field resolver. This is typically
    a map or a scalar; for fields that are lists, this will be
    a seq of such values.

    Will block until the ResolverResult is realized.")

  (resolve-errors [this]
    "Returns any errors that were generated while resolving the value.

    This may be a single map, or a seq of maps.
    Each map must contain a :message key and may contain additional
    keys. Further keys are added to identify the executing field.

    Will block until the ResolverResult is realized.")

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
    "Invoked to realize the ResolverResult, triggering the callback and unblocking anyone waiting for the resolved-value or errors.

    Returns the deferred resolver result."))

(defrecord ^:private ResolverResultImpl [resolved-value resolve-errors]

  ResolverResult

  (resolved-value [_] resolved-value)

  (resolve-errors [_] resolve-errors)

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

      (resolved-value [_]

        (resolved-value @realized-result))

      (resolve-errors [_]
        (resolve-errors @realized-result))

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
          (throw (IllegalStateException. "May only realize a DeferredResolverResult once.")))

        (deliver realized-result (resolve-as resolved-value errors))

        (when (realized? callback-promise)
          (@callback-promise resolved-value errors))

        this))))

(defn combine-results
  "Given a left and a right ResolverResult, returns a new ResolverResult that combines
  the realized values using the provided function."
  [f left-result right-result]
  (let [combined-result (resolve-promise)]
    (on-deliver! left-result
                 (fn [left-value _]
                   (on-deliver! right-result
                                (fn [right-value _]
                                  (deliver! combined-result (f left-value right-value))))))
    combined-result))
