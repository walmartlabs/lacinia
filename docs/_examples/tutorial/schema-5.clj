(ns my.clojure-game-geek.schema
  "Contains custom resolvers and a function to provide the full schema."
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [my.clojure-game-geek.db :as db]
            [clojure.edn :as edn]))

(defn game-by-id
  [db]
  (fn [_ args _]
    (db/find-game-by-id db (:id args))))

(defn member-by-id
  [db]
  (fn [_ args _]
    (db/find-member-by-id db (:id args))))

(defn board-game-designers
  [db]
  (fn [_ _ board-game]
    (db/list-designers-for-game db (:id board-game))))

(defn designer-games
  [db]
  (fn [_ _ designer]
    (db/list-games-for-designer db (:id designer))))

(defn rating-summary
  [db]
  (fn [_ _ board-game]
    (let [ratings (map :rating (db/list-ratings-for-game db (:id board-game)))
          n (count ratings)]
      {:count   n
       :average (if (zero? n)
                  0
                  (/ (apply + ratings)
                    (float n)))})))

(defn member-ratings
  [db]
  (fn [_ _ member]
    (db/list-ratings-for-member db (:id member))))

(defn game-rating->game
  [db]
  (fn [_ _ game-rating]
    (db/find-game-by-id db (:game-id game-rating))))

(defn resolver-map
  [component]
  (let [{:keys [db]} component]
    {:Query/gameById          (game-by-id db)
     :Query/memberById        (member-by-id db)
     :BoardGame/designers     (board-game-designers db)
     :BoardGame/ratingSummary (rating-summary db)
     :Designer/games          (designer-games db)
     :Member/ratings          (member-ratings db)
     :GameRating/game         (game-rating->game db)}))

(defn load-schema
  [component]
  (-> (io/resource "cgg-schema.edn")
    slurp
    edn/read-string
    (util/inject-resolvers (resolver-map component))
    schema/compile))

(defrecord SchemaProvider [db schema]

  component/Lifecycle

  (start [this]
    (assoc this :schema (load-schema this)))

  (stop [this]
    (assoc this :schema nil)))
