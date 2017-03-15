(ns com.walmartlabs.lacinia.resolve
  "Complex results for field resolver functions.")

;; This can be extended in the future to encompass a value that may be resolved
;; asynchronously.
(defprotocol ResolverResult
  "A complex returned from a field resolver that can contain a resolved value
  and/or errors."

  (resolved-value [this]
    "Returns the value resolved by the field resolver. This is typically
    a map or a scalar; for fields that are lists, this will be
    a seq of such values.")

  (resolve-errors [this]
    "Returns any errors that were generated while resolving the value.

    This may be a single map, or a seq of maps.
    Each map must contain a :message key and may contain additional
    keys. Further keys are added to identify the executing field."))

(defrecord ^:private ResolverResultImpl [resolved-value resolve-errors]

  ResolverResult

  (resolved-value [_] resolved-value)

  (resolve-errors [_] resolve-errors))

(defn resolve-as
  "Invoked by field resolvers to wrap a simple return value as a ResolverResult."
  ([resolved-value]
   (resolve-as resolved-value nil))
  ([resolved-value resolver-errors]
   (->ResolverResultImpl resolved-value resolver-errors)))
