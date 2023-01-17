(ns my.clojure-game-geek.schema
  "Contains custom resolvers and a function to provide the full schema."
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia.util :as util]
            [com.walmartlabs.lacinia.schema :as schema]
            [clojure.edn :as edn]))

(defn resolve-element-by-id
  [element-map]
  (fn [context args value]
    (let [{:keys [id]} args]
      (get element-map id))))

(defn resolve-board-game-designers
  [designers-map context args board-game]
  (->> board-game
    :designers
    (map designers-map)))

(defn resolve-designer-games
  [games-map context args designer]
  (let [{:keys [id]} designer]
    (->> games-map
      vals
      (filter #(-> % :designers (contains? id))))))

(defn entity-map
  [data k]
  (reduce #(assoc %1 (:id %2) %2)
    {}
    (get data k)))

(defn rating-summary
  [ratings]
  (fn [_ _ board-game]
    (let [id (:id board-game)
          ratings' (->> ratings
                     (filter #(= id (:game-id %)))
                     (map :rating))
          n (count ratings')]
      {:count   n
       :average (if (zero? n)
                  0
                  (/ (apply + ratings')
                    (float n)))})))

(defn member-ratings
  [ratings-map]
  (fn [_ _ member]
    (let [id (:id member)]
      (filter #(= id (:member-id %)) ratings-map))))

(defn game-rating->game
  [games-map]
  (fn [_ _ game-rating]
    (get games-map (:game-id game-rating))))

(defn resolver-map
  []
  (let [cgg-data (-> (io/resource "cgg-data.edn")
                   slurp
                   edn/read-string)
        games-map (entity-map cgg-data :games)
        designers-map (entity-map cgg-data :designers)
        members-map (entity-map cgg-data :members)
        ratings (:ratings cgg-data)]
    {:Query/gameById          (resolve-element-by-id games-map)
     :Query/memberById        (resolve-element-by-id members-map)
     :BoardGame/designers     (partial resolve-board-game-designers designers-map)
     :BoardGame/ratingSummary (rating-summary ratings)
     :Designer/games          (partial resolve-designer-games games-map)
     :Member/ratings          (member-ratings ratings)
     :GameRating/game         (game-rating->game games-map)}))

(defn load-schema
  []
  (-> (io/resource "cgg-schema.edn")
    slurp
    edn/read-string
    (util/inject-resolvers (resolver-map))
    schema/compile))

(defrecord SchemaProvider [schema]

  component/Lifecycle

  (start [this]
    (assoc this :schema (load-schema)))

  (stop [this]
    (assoc this :schema nil)))
