(ns com.walmartlabs.lacinia
  (:require [com.walmartlabs.lacinia.parser.query :as parser]
            [com.walmartlabs.lacinia.constants :as constants]
            [com.walmartlabs.lacinia.executor :as executor]
            [com.walmartlabs.lacinia.validator :as validator]
            [com.walmartlabs.lacinia.internal-utils :refer [cond-let]]
            [com.walmartlabs.lacinia.util :refer [as-error-map]]
            [com.walmartlabs.lacinia.resolve :as resolve])
  (:import (clojure.lang ExceptionInfo)))

(defn ^:private as-errors
  [exception]
  {:errors [(as-error-map exception)]})

(defn execute-parsed-query-async
  "Prepares a query, by applying query variables to it, resulting in a prepared
  query which is then executed.

  Returns a [[ResolverResult]] that will deliver the result map.."
  {:added "0.16.0"}
  [parsed-query variables context]
  {:pre [(map? parsed-query)
         (or (nil? context)
             (map? context))]}
  (cond-let
    :let [[prepared error-result] (try
                                    [(parser/prepare-with-query-variables parsed-query variables)]
                                    (catch Exception e
                                      [nil (as-errors e)]))]

    (some? error-result)
    (resolve/resolve-as error-result)

    :let [validation-errors (validator/validate prepared)]

    (seq validation-errors)
    (resolve/resolve-as {:errors validation-errors})

    :else
    (executor/execute-query (assoc context constants/parsed-query-key prepared))))

(defn execute-parsed-query
  "Prepares a query, by applying query variables to it, resulting in a prepared
  query which is then executed.

  Returns a result map (with :data and/or :errors keys)."
  [parsed-query variables context]
  (let [result-promise (promise)
        execution-result (execute-parsed-query-async parsed-query variables context)]
    (resolve/on-deliver! execution-result
                         (fn [result]
                           (deliver result-promise result)))
    ;; Block on that deliver, then return the final result.
    @result-promise))

(defn execute
  "Given a compiled schema and a query string, attempts to execute it.

  Returns a result map with up-to two keys:  :data is the main result of the
  execution, and :errors are any errors generated during execution.

  In the case where there's a parse or validation problem for the query,
  just the :errors key will be present.

  schema
  : GraphQL schema (as compiled by [[com.walmartlabs.lacinia.schema/compile]]).

  query
  : Input query string to be parsed and executed.

  variables
  : compile-time variables that can be referenced inside the query using the
    `$variable-name` production. A map of keyword keys and values.

  context (optional)
  : Additional data that will ultimately be passed to resolver functions.

  This function parses the query and invokes [[execute-parsed-query]]."
  ([schema query variables context]
   (execute schema query variables context {}))
  ([schema query variables context {:keys [operation-name] :as options}]
   {:pre [(string? query)]}
   (let [[parsed error-result] (try
                                 [(parser/parse-query schema query operation-name)]
                                 (catch ExceptionInfo e
                                   [nil (as-errors e)]))]
     (if (some? error-result)
       error-result
       (execute-parsed-query parsed variables context)))))
