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

(ns com.walmartlabs.lacinia.subscription-tests
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.parser :as parser]
    [com.walmartlabs.lacinia.executor :as executor]
    [com.walmartlabs.lacinia.constants :as constants]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.selection :as selection]
    [com.walmartlabs.test-utils :as test-utils :refer [simplify]]))

;; There's not a whole lot we can do here, as most of the support has to come from the web tier code, e.g.,
;; pedestal-lacinia.

(def ^:private *latest-log-event (atom nil))
(def ^:private *latest-response (atom nil))
(def ^:private *instrumentation (atom 0))

(def ^:private ^:dynamic *verbose* false)

(defn ^:private log-event [event]
  (reset! *latest-log-event event))

(defn ^:private latest-response
  []
  ;; Give async stuff a chance to run
  (Thread/sleep 10)
  (simplify @*latest-response))

(defn ^:private stream-logs
  [_context args source-stream]
  (let [{:keys [severity fakeError]} args
        watch-key (gensym)]
    (add-watch *latest-log-event watch-key
               (fn [_ _ _ log-event]
                 (when *verbose*
                   (prn :log-event-received log-event))
                 (cond
                   (nil? log-event)
                   (source-stream nil)

                   fakeError
                   (source-stream (resolve/resolve-as log-event {:message "Expected error"}))

                   (or (nil? severity)
                       (= severity (:severity log-event)))
                   (source-stream log-event))))
    #(remove-watch *latest-log-event watch-key)))

(defn ^:private apply-subscription-field-directives
  [fdef streamer]
  (let [directives (-> fdef
                       selection/directives
                       keys
                       set)]
    (when (directives :instrument)
      (fn [context args source-stream]
        (swap! *instrumentation inc)
        (streamer context args source-stream)))))

(def ^:private compiled-schema
  (-> (io/resource "subscriptions-schema.edn")
      slurp
      edn/read-string
      (util/attach-streamers {:stream-logs stream-logs})
      (schema/compile {:apply-subscription-field-directives apply-subscription-field-directives})))

(defn ^:private execute
  ([query-string vars]
   (execute compiled-schema query-string vars))
  ([schema query-string vars]
   (let [prepared-query (-> (parser/parse-query schema query-string)
                            (parser/prepare-with-query-variables vars))
         *cleanup-callback (promise)
         context {constants/parsed-query-key prepared-query}
         ;; For compatibility reasons, the value may be a ResolvedValue.
         source-stream (fn accept-value [value]
                         (cond
                           (nil? value)
                           (do
                             (@*cleanup-callback)
                             (reset! *latest-response nil))

                           (resolve/is-resolver-result? value)
                           (resolve/on-deliver! value accept-value)

                           :else
                           (resolve/on-deliver!
                             (executor/execute-query (assoc context
                                                            ::executor/resolved-value value))
                             (fn [result]
                               (reset! *latest-response result)))))]
     (deliver *cleanup-callback (executor/invoke-streamer context source-stream)))))

(deftest basic-subscription
  (reset! *instrumentation 0)

  (execute "subscription { logs {  message }}" nil)

  (is (zero? @*instrumentation))
  (is (nil? (latest-response)))

  (log-event {:message "first"})

  (is (= {:data {:logs {:message "first"}}}
         (latest-response)))

  (log-event {:message "second"})

  (is (= {:data {:logs {:message "second"}}}
         (latest-response)))


  (log-event nil)

  (is (nil? (latest-response)))

  ;; Further "events" do nothing.

  (log-event {:message "ignored"})

  (is (nil? (latest-response))))

(deftest ensure-variables-and-arguments-are-resolved
  (execute "subscription ($severity : String) { logs (severity: $severity) { severity message }}"
           {:severity "critical"})

  (log-event {:severity "normal" :message "first"})

  (is (nil? (latest-response)))

  (log-event {:severity "critical" :message "second"})

  (is (= {:data {:logs {:message "second"
                        :severity "critical"}}}
         (latest-response)))

  (log-event {:severity "normal" :message "third"})

  (is (= {:data {:logs {:message "second"
                        :severity "critical"}}}
         (latest-response)))

  (log-event nil)

  (is (nil? (latest-response))))

(deftest one-subscription-per-request
  (when-let [e (is (thrown? Exception (parser/parse-query compiled-schema
                                                          "subscription { sev: logs { severity } msg: logs { message }}")))]
    (is (= "Subscriptions only allow exactly one selection for the operation."
           (.getMessage e)))))

(deftest errors-are-returned
  (execute "subscription { logs(fakeError: true) { severity message } }" {})

  (log-event {:severity "critical" :message "first"})

  (is (= {:data {:logs {:message "first"
                               :severity "critical"}}
          :errors [{:message "Expected error"
                    :locations [{:line 1, :column 16}]
                    :path [:logs]
                    :extensions {:arguments {:fakeError true}}}]}
         (latest-response)))

  (log-event nil)

  (is (nil? (latest-response))))

(deftest introspection
  (is (= {:data
          {:__schema
           {:subscriptionType
            {:description nil
             :fields [{:args [{:name "fakeError"
                               :type {:name "Boolean"}}
                              {:name "severity"
                               :type {:name "String"}}]
                       :name "directive_logs"
                       :type {:name "LogEvent"}}
                      {:args [{:name "fakeError"
                               :type {:name "Boolean"}}
                              {:name "severity"
                               :type {:name "String"}}]
                       :name "logs"
                       :type {:name "LogEvent"}}]}
            :types [{:name "Boolean"}
                    {:name "Float"}
                    {:name "ID"}
                    {:name "Int"}
                    {:name "LogEvent"}
                    {:name "Query"}
                    {:name "String"}
                    {:name "Subscription"}]}}}
         (test-utils/execute compiled-schema "{ __schema { types { name } subscriptionType { description fields { name type { name } args { name type { name }}}}}}"))))

(deftest subscription-with-directive
  (reset! *instrumentation 0)
  (execute "subscription { directive_logs { message }}" nil)
  (is (= 1 @*instrumentation)))


(deftest subscription-is-passed-selection
  (let [*selections (atom nil)
        *args (atom nil)
        streamer (fn [context args _]
                   (reset! *args args)
                   (reset! *selections (executor/selections-seq context))
                   nil)
        schema (-> "subscription-selection.edn"
                   io/resource
                   slurp
                   edn/read-string
                   (util/inject-streamers {:Subscription/time_from streamer})
                   schema/compile)]
    (execute schema
                    "subscription ($when : String!) { time_from (when: $when) { hour minute }}"
                    {:when "now"})
    (log-event nil)

    (is (= {:when "now"
            :interval 60} @*args))
    (is (= [:Instant/hour :Instant/minute] @*selections))))