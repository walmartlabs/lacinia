(ns my.clojure-game-geek.db
  (:require [clojure.edn :as edn]
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
