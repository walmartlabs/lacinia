(ns my.clojure-game-geek.db
  (:require [clojure.java.jdbc :as jdbc]
            [io.pedestal.log :as log]
            [clojure.string :as string]
            [clojure.set :as set]
            [com.stuartsierra.component :as component])
  (:import (com.mchange.v2.c3p0 ComboPooledDataSource)))

(defn- pooled-data-source
  [host dbname user password port]
  (doto (ComboPooledDataSource.)
    (.setDriverClass "org.postgresql.Driver")
    (.setJdbcUrl (str "jdbc:postgresql://" host ":" port "/" dbname))
    (.setUser user)
    (.setPassword password)))

(defrecord ClojureGameGeekDb [^ComboPooledDataSource datasource]

  component/Lifecycle

  (start [this]
    (assoc this :datasource (pooled-data-source "localhost" "cggdb" "cgg_role" "lacinia" 25432)))

  (stop [this]
    (.close datasource)
    (assoc this :datasource nil)))

(defn- query
  [component statement]
  (let [[sql & params] statement]
    (log/debug :sql (string/replace sql #"\s+" " ")
      :params params))
  (jdbc/query component statement))

(defn- execute!
  [component statement]
  (let [[sql & params] statement]
    (log/debug :sql (string/replace sql #"\s+" " ")
      :params params))
  (jdbc/execute! component statement))

(defn- remap-board-game
  [row-data]
  (set/rename-keys row-data {:game_id     :id
                             :min_players :minPlayers
                             :max_players :maxPlayers
                             :created_at  :createdAt
                             :updated_at  :updatedAt}))

(defn- remap-member
  [row-data]
  (set/rename-keys row-data {:member_id  :id
                             :created_at :createdAt
                             :updated_at :updatedAt}))

(defn- remap-designer
  [row-data]
  (set/rename-keys row-data {:designer_id :id
                             :created_at  :createdAt
                             :updated_at  :updatedAt}))

(defn- remap-rating
  [row-data]
  (set/rename-keys row-data {:member_id  :member-id
                             :game_id    :game-id
                             :created_at :createdAt
                             :updated_at :updatedAt}))

(defn find-game-by-id
  [component game-id]
  (-> (query component
        ["select game_id, name, summary, min_players, max_players, created_at, updated_at
               from board_game where game_id = ?" game-id])
    first
    remap-board-game))

(defn find-member-by-id
  [component member-id]
  (-> (query component
        ["select member_id, name, created_at, updated_at
             from member
             where member_id = ?" member-id])
    first
    remap-member))

(defn list-designers-for-game
  [component game-id]
  (->> (query component
         ["select d.designer_id, d.name, d.uri, d.created_at, d.updated_at
             from designer d
             inner join designer_to_game j on (d.designer_id = j.designer_id)
             where j.game_id = ?
             order by d.name" game-id])
    (map remap-designer)))

(defn list-games-for-designer
  [component designer-id]
  (->> (query component
         ["select g.game_id, g.name, g.summary, g.min_players, g.max_players, g.created_at,
                g.updated_at
              from board_game g
              inner join designer_to_game j on (g.game_id = j.game_id)
              where j.designer_id = ?
              order by g.name" designer-id])
    (map remap-board-game)))

(defn list-ratings-for-game
  [component game-id]
  (->> (query component
         ["select game_id, member_id, rating, created_at, updated_at
              from game_rating
              where game_id = ?" game-id])
    (map remap-rating)))

(defn list-ratings-for-member
  [component member-id]
  (->> (query component
         ["select game_id, member_id, rating, created_at, updated_at
              from game_rating
              where member_id = ?" member-id])
    (map remap-rating)))

(defn upsert-game-rating
  "Adds a new game rating, or changes the value of an existing game rating.

  Returns nil."
  [component game-id member-id rating]
  (execute! component
    ["insert into game_rating (game_id, member_id, rating)
          values (?, ?, ?)
          on conflict (game_id, member_id) do update set rating = ?"
     game-id member-id rating rating])
  nil)
