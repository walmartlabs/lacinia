(ns my.clojure-game-geek.system
  (:require [com.stuartsierra.component :as component]
            [my.clojure-game-geek.schema :as schema]
            [my.clojure-game-geek.server :as server]
            [my.clojure-game-geek.db :as db]))

(defn new-system
  ([]
   (new-system nil))
  ([opts]
   (let [{:keys [port]
          :or {port 8888}} opts]
     (assoc (component/system-map)
       :db (db/map->ClojureGameGeekDb {})
       :server (component/using (server/map->Server {:port port})
                 [:schema-provider])
       :schema-provider (component/using
                          (schema/map->SchemaProvider {})
                          [:db])))))
