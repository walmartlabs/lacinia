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

  (when-ready! [this callback]
    "Provides a callback that is invoked immediately after the ResolverResult is realized.
    The callback is passed the ResolverResult's value and errors.

    `when-ready!` should only be invoked once.
    It returns the ResolverResult.

    The callback is invoked for side-effects; it's result is ignored."))

(defprotocol DeferredResolverResult
  "A specialization of ResolverResult that supports asynchronous realization of the result."

  (resolve-async!
    [this value]
    [this value errors]
    "Invoked to realized the result, triggering the callback and unblocking anyone waiting for the resolved-value or errors.

    Returns the deferred resolver result."))

(defrecord ^:private ResolverResultImpl [resolved-value resolve-errors]

  ResolverResult

  (resolved-value [_] resolved-value)

  (resolve-errors [_] resolve-errors)

  (when-ready! [this callback]
    (callback resolved-value resolve-errors)
    this))

(defn resolve-as
  "Invoked by field resolvers to wrap a simple return value as a ResolverResult.

  This is an immediately realized ResolverResult."
  ([resolved-value]
   (resolve-as resolved-value nil))
  ([resolved-value resolver-errors]
   (->ResolverResultImpl resolved-value resolver-errors)))

(defn deferred-resolve
  "Returns a DeferredResolverResult."
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
      (when-ready! [this callback]
        (cond
          (realized? realized-result)
          (when-ready! @realized-result callback)

          (realized? callback-promise)
          (throw (IllegalStateException. "DeferredResolverResult callback may only be set once, and only before the result is realized."))

          :else
          (deliver callback-promise callback))

        this)

      DeferredResolverResult

      (resolve-async! [this resolved-value]
        (resolve-async! this resolved-value nil))

      (resolve-async! [this resolved-value errors]
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
  (let [combined-result (deferred-resolve)]
    (when-ready! left-result
                 (fn [left-value _]
                   (when-ready! right-result
                                (fn [right-value _]
                                  (resolve-async! combined-result (f left-value right-value))))))
    combined-result))
