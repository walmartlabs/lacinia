(ns my.clojure-game-geek.system
  (:require [com.stuartsierra.component :as component]
            [my.clojure-game-geek.schema :as schema]
            [my.clojure-game-geek.server :as server]))

(defn new-system
  []
  (merge (component/system-map)
         (server/new-server)
         (schema/new-schema-provider)))
