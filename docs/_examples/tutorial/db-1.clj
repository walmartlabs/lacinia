(ns clojure-game-geek.db
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [com.stuartsierra.component :as component]))

(defrecord ClojureGameGeekDb [data]

  component/Lifecycle

  (start [this]
    (assoc this :data (-> (io/resource "cgg-data.edn")
                          slurp
                          edn/read-string
                          atom)))

  (stop [this]
    (assoc this :data nil)))

(defn new-db
  []
  {:db (map->ClojureGameGeekDb {})})

(defn find-game-by-id
  [db game-id]
  (->> db
       :data
       deref
       :games
       (filter #(= game-id (:id %)))
       first))

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
       (filter #(= game-id (:game_id %)))))

(defn list-ratings-for-member
  [db member-id]
  (->> db
       :data
       deref
       :ratings
       (filter #(= member-id (:member_id %)))))
