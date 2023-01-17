(ns my.clojure-game-geek.server
  (:require [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.pedestal2 :as lp]
            [io.pedestal.http :as http]))

(defrecord Server [schema-provider server port]

  component/Lifecycle

  (start [this]
    (assoc this :server (-> schema-provider
                          :schema
                          (lp/default-service {:port port})
                          http/create-server
                          http/start)))

  (stop [this]
    (http/stop server)
    (assoc this :server nil)))
