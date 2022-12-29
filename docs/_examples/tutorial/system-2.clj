(ns my.clojure-game-geek.system
  (:require [com.stuartsierra.component :as component]
            [my.clojure-game-geek.schema :as schema]
            [my.clojure-game-geek.server :as server]
            [my.clojure-game-geek.db :as db]))

(defn new-system
  []
  (assoc (component/system-map)
    :db (db/map->ClojureGameGeekDb {})
    :server (component/using (server/map->Server {})
              [:schema-provider])
    :schema-provider (component/using
                       (schema/map->SchemaProvider {})
                       [:db])))
