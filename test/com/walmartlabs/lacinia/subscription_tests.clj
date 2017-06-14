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
    [com.walmartlabs.test-utils :refer [simplify instrument-schema-namespace]])
  (:import (clojure.lang ExceptionInfo)))

(instrument-schema-namespace)

;; There's not a whole lot we can do here, as most of the support has to come from the web tier code, e.g.,
;; pedestal-lacinia.
(def ^:private *latest-log-event (atom nil))
(def ^:private *latest-render (atom nil))

(def ^:private ^:dynamic *verbose* false)

(defn ^:private log-event [event]
  (reset! *latest-log-event event))

(defn ^:private latest-render
  []
  (simplify @*latest-render))

(defn ^:private stream-logs
  [context args event-handler]
  (let [{:keys [severity]} args
        watch-key (gensym)]
    (add-watch *latest-log-event watch-key
               (fn [_ _ _ log-event]
                 (when *verbose*
                   (prn :log-event-received log-event))
                 (when (or (nil? log-event)
                           (nil? severity)
                           (= severity (:severity log-event)))
                   (event-handler log-event))))
    #(remove-watch *latest-log-event watch-key)))


(def ^:private compiled-schema
  (-> (io/resource "subscriptions-schema.edn")
      slurp
      edn/read-string
      (util/attach-resolvers {:resolve-logs (fn [_ args log-event]
                                              (when *verbose*
                                                (prn :log-event-render args log-event))
                                              log-event)})
      (util/attach-streamers {:stream-logs stream-logs})
      schema/compile))

(defn ^:private execute
  [query-string vars]
  (let [prepared-query (-> (parser/parse-query compiled-schema query-string)
                           (parser/prepare-with-query-variables vars))
        *cleanup-callback (promise)
        context {constants/parsed-query-key prepared-query}
        event-handler (fn [value]
                        (if (some? value)
                          (resolve/on-deliver!
                            (executor/execute-query (assoc context
                                                           ::executor/resolved-value value))
                            (fn [result _]
                              (reset! *latest-render result)))
                          (do
                            (@*cleanup-callback)
                            (reset! *latest-render nil))))]
    (deliver *cleanup-callback (executor/invoke-streamer context event-handler))))

(deftest basic-subscription

  (execute "subscription { logs {  message }}" nil)

  (is (nil? (latest-render)))

  (log-event {:message "first"})

  (is (= {:data {:logs {:message "first"}}}
         (latest-render)))

  (log-event {:message "second"})

  (is (= {:data {:logs {:message "second"}}}
         (latest-render)))


  (log-event nil)

  (is (nil? (latest-render)))

  ;; Further "events" do nothing.

  (log-event {:message "ignored"})

  (is (nil? (latest-render))))

(deftest ensure-variables-and-arguments-are-resolved
  (execute "
subscription ($severity : String) {

  logs (severity: $severity) {
     severity message
  }
}" {:severity "critical"})

  (log-event {:severity "normal" :message "first"})

  (is (nil? (latest-render)))

  (log-event {:severity "critical" :message "second"})

  (is (= {:data {:logs {:message "second"
                        :severity "critical"}}}
         (latest-render)))

  (log-event {:severity "normal" :message "third"})

  (is (= {:data {:logs {:message "second"
                        :severity "critical"}}}
         (latest-render)))

  (log-event nil)

  (is (nil? (latest-render))))

(deftest one-subscription-per-request
  (when-let [e (is (thrown? Exception (parser/parse-query compiled-schema "
subscription {
  sev: logs { severity }
  msg: logs { message }
}")))]
    (is (= "Subscriptions only allow exactly one selection for the operation." (.getMessage e)))))


