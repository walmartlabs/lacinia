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

(ns com.walmartlabs.lacinia
  (:require [com.walmartlabs.lacinia.parser :as parser]
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

  Returns a [[ResolverResult]] that will deliver the result map, or an exception."
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

  Returns a result map (with :data and/or :errors keys), or an exception if
  execution failed.

  Options as per [[execute]]."
  ([parsed-query variables context]
   (execute-parsed-query parsed-query variables context nil))
  ([parsed-query variables context options]
   (let [*result (promise)
         {:keys [timeout-ms timeout-error]
          :or {timeout-ms 0
               timeout-error {:message "Query execution timed out."}}} options
         execution-result (execute-parsed-query-async parsed-query variables context)
         result (do
                  (resolve/on-deliver! execution-result *result)
                  ;; Block on that deliver, then return the final result.
                  (if (pos? timeout-ms)
                    (deref *result
                           timeout-ms
                           {:errors [timeout-error]})
                    @*result))]
     (when (instance? Throwable result)
       (throw result))

     result)))

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
    `$variable-name` production.

  context (optional)
  : Additional data that will ultimately be passed to resolver functions.

  options (optional)
  : Additional options to control execution.

  Options:

  :operation-name
  : Identifies which operation to execute, when the query specifies more than one.

  :timeout-ms
  : Timeout for the operation.  Defaults to 0, for no error.

  :timeout-error
  : Error map used if a timeout occurs.

  This function parses the query and invokes [[execute-parsed-query]].

  When a GraphQL query contains variables, the values for those variables
  arrive seperately; for example, a JSON request may have the query
  in the \"query\" property, and the variables in the \"variables\" property.

  The values for those variables are provided in the variables parameter.
  The keys are keyword-ized names of the variable (without the leading
  '$'); if a variable is named `$user_id` in the query, the corresponding
  key should be `:user_id`.

  The values in the variables map should be of a type matching the
  variable's declaration in the query; typically a string or other scalar value,
  or a map for a variable of type InputObject."
  ([schema query variables context]
   (execute schema query variables context {}))
  ([schema query variables context options]
   {:pre [(string? query)]}
   (let [{:keys [operation-name]} options
         [parsed error-result] (try
                                 [(parser/parse-query schema query operation-name)]
                                 (catch ExceptionInfo e
                                   [nil (as-errors e)]))]
     (if (some? error-result)
       error-result
       (execute-parsed-query parsed variables context options)))))
