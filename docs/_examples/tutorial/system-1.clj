(ns my.clojure-game-geek.system
  (:require [com.stuartsierra.component :as component]
            [my.clojure-game-geek.schema :as schema]
            [my.clojure-game-geek.server :as server]))

(defn new-system
  []
  (assoc (component/system-map)
    :server (component/using (server/map->Server {})
              [:schema-provider])
    :schema-provider (schema/map->SchemaProvider {})))
