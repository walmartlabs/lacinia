(ns com.walmartlabs.lacinia
  (:require [com.walmartlabs.lacinia.parser :as parser]
            [com.walmartlabs.lacinia.constants :as constants]
            [com.walmartlabs.lacinia.executor :as executor]
            [com.walmartlabs.lacinia.validator :as validator]
            [com.walmartlabs.lacinia.internal-utils :refer [cond-let to-message]])
  (:import (clojure.lang ExceptionInfo)))

(defn ^:private as-errors
  [exception]
  [(merge {:message (to-message exception)}
          (ex-data exception))])

(defn execute-parsed-query
  "Prepares a query, by applying query variables to it, resulting in a prepared
  query which is then executed."
  [parsed-query variables context]
  {:pre [(map? parsed-query)
         (or (nil? context)
             (map? context))]}
  (cond-let
    :let [schema (get parsed-query constants/schema-key)
          [prepared error-result] (try
                                    [(parser/prepare-with-query-variables parsed-query variables)]
                                    (catch Exception e
                                      [nil {:errors (as-errors e)}]))]

    (some? error-result)
    error-result

    :let [validation-errors (validator/validate schema prepared {})]

    (seq validation-errors)
    {:errors validation-errors}

    :else
    (try
      (executor/execute-query (assoc context
                                constants/schema-key schema
                                constants/parsed-query-key prepared))
      (catch Exception e
        ;; Include a nil :data key to indicate that it is an execution time
        ;; exception, rather than a query parse/prepare/validation exception.
        {:data   nil
         :errors (as-errors e)}))))


(defn execute
  "Given a compiled schema and a query string, attempts to execute it.

  Returns a map with up-to two keys:  :data is the main result of the
  execution, and :errors are any errors generated during execution.

  In the case where there's a parse or validation problem for the query,
  just the :errors key will be present.

  schema
  : GraphQL schema (as compiled by [[com.walmartlabs.lacinia.schema/compile]]).

  query
  : Input query string to be parsed and executed.

  variables
  : compile-time variables that can be referenced inside the query using the
    `$variable-name` production.

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
                                   [nil {:errors (as-errors e)}]))]
     (if (some? error-result)
       error-result
       (execute-parsed-query parsed variables context)))))
