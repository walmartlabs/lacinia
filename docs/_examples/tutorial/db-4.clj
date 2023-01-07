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

(defn- remap-board-game
  [row-data]
  (set/rename-keys row-data {:game_id     :id
                             :min_players :minPlayers
                             :max_players :maxPlayers
                             :created_at  :createdAt
                             :updated_at  :updatedAt}))

(defn find-game-by-id
  [component game-id]
  (-> (query component
        ["select game_id, name, summary, min_players, max_players, created_at, updated_at
               from board_game where game_id = ?" game-id])
    first
    remap-board-game))

(defn find-member-by-id
  [db member-id]
  (->> db
    :data
    deref
    :members
    (filter #(= member-id (:id %)))
    first))

(defn list-designers-for-game
  [db game-id]
  (let [designers (:designers (find-game-by-id db game-id))]
    (->> db
      :data
      deref
      :designers
      (filter #(contains? designers (:id %))))))

(defn list-games-for-designer
  [db designer-id]
  (->> db
    :data
    deref
    :games
    (filter #(-> % :designers (contains? designer-id)))))

(defn list-ratings-for-game
  [db game-id]
  (->> db
    :data
    deref
    :ratings
    (filter #(= game-id (:game-id %)))))

(defn list-ratings-for-member
  [db member-id]
  (->> db
    :data
    deref
    :ratings
    (filter #(= member-id (:member-id %)))))

(defn ^:private apply-game-rating
  [game-ratings game-id member-id rating]
  (->> game-ratings
    (remove #(and (= game-id (:game-id %))
               (= member-id (:member-id %))))
    (cons {:game-id   game-id
           :member-id member-id
           :rating    rating})))

(defn upsert-game-rating
  "Adds a new game rating, or changes the value of an existing game rating."
  [db game-id member-id rating]
  (-> db
    :data
    (swap! update :ratings apply-game-rating game-id member-id rating)))
