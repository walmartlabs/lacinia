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
    [com.walmartlabs.test-utils :as test-utils
     :refer [simplify instrument-schema-namespace]])
  (:import (clojure.lang ExceptionInfo)))

(instrument-schema-namespace)

;; There's not a whole lot we can do here, as most of the support has to come from the web tier code, e.g.,
;; pedestal-lacinia.
(def ^:private *latest-log-event (atom nil))
(def ^:private *latest-response (atom nil))

(def ^:private ^:dynamic *verbose* false)

(defn ^:private log-event [event]
  (reset! *latest-log-event event))

(defn ^:private latest-response
  []
  (simplify @*latest-response))

(defn ^:private stream-logs
  [context args source-stream]
  (let [{:keys [severity]} args
        watch-key (gensym)]
    (add-watch *latest-log-event watch-key
               (fn [_ _ _ log-event]
                 (when *verbose*
                   (prn :log-event-received log-event))
                 (when (or (nil? log-event)
                           (nil? severity)
                           (= severity (:severity log-event)))
                   (source-stream log-event))))
    #(remove-watch *latest-log-event watch-key)))


(def ^:private compiled-schema
  (-> (io/resource "subscriptions-schema.edn")
      slurp
      edn/read-string
      (util/attach-streamers {:stream-logs stream-logs})
      schema/compile))

(defn ^:private execute
  [query-string vars]
  (let [prepared-query (-> (parser/parse-query compiled-schema query-string)
                           (parser/prepare-with-query-variables vars))
        *cleanup-callback (promise)
        context {constants/parsed-query-key prepared-query}
        source-stream (fn [value]
                        (if (some? value)
                          (resolve/on-deliver!
                            (executor/execute-query (assoc context
                                                           ::executor/resolved-value value))
                            (fn [result _]
                              (reset! *latest-response result)))
                          (do
                            (@*cleanup-callback)
                            (reset! *latest-response nil))))]
    (deliver *cleanup-callback (executor/invoke-streamer context source-stream))))

(deftest basic-subscription

  (execute "subscription { logs {  message }}" nil)

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


(deftest introspection
  (is (= {:data
          {:__schema
           {:subscriptionType
            {:description "Root of all subscriptions."
             :fields [{:args [{:name "severity"
                               :type {:name "String"}}]
                       :name "logs"
                       :type {:name "LogEvent"}}]}
            :types [{:name "Boolean"}
                    {:name "Float"}
                    {:name "ID"}
                    {:name "Int"}
                    {:name "LogEvent"}
                    {:name "QueryRoot"}
                    {:name "String"}
                    {:name "SubscriptionRoot"}]}}}
         (test-utils/execute compiled-schema "{ __schema { types { name } subscriptionType { description fields { name type { name } args { name type { name }}}}}}"))))
