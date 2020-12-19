(ns clojure-game-geek.system-tests
  (:require
    [clojure.test :refer [deftest is]]
    [clojure-game-geek.system :as system]
    [clojure-game-geek.test-utils :refer [simplify]]
    [com.stuartsierra.component :as component]
    [com.walmartlabs.lacinia :as lacinia]))

(defn ^:private test-system
  "Creates a new system suitable for testing, and ensures that
  the HTTP port won't conflict with a default running system."
  []
  (-> (system/new-system)
      (assoc-in [:server :port] 8989)))

(defn ^:private q
  "Extracts the compiled schema and executes a query."
  [system query variables]
  (-> system
      (get-in [:schema-provider :schema])
      (lacinia/execute query variables nil)
      simplify))

(deftest can-read-board-game
  (let [system (component/start-system (test-system))
        results (q system
                   "{ game_by_id(id: 1234) { name summary min_players max_players play_time }}"
                   nil)]
    (is (= {:data {:game_by_id {:max_players 2
                                :min_players 2
                                :name "Zertz"
                                :play_time nil
                                :summary "Two player abstract with forced moves and shrinking board"}}}
           results))
    (component/stop-system system)))
