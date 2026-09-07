(ns my.clojure-game-geek.system-test
  (:require [clojure.test :refer [deftest is]]
            [com.stuartsierra.component :as component]
            [com.walmartlabs.lacinia :as lacinia]
            [my.clojure-game-geek.test-utils :refer [simplify]]
            [my.clojure-game-geek.system :as system]))

(defn- test-system
  "Creates a new system suitable for testing, and ensures that
  the HTTP port won't conflict with a default running system."
  []
  (system/new-system {:port 8989}))

(defn- q
  "Extracts the compiled schema and executes a query."
  [system query variables]
  (-> system
    (get-in [:schema-provider :schema])
    (lacinia/execute query variables nil)
    simplify))

(deftest can-read-board-game
  (let [system (component/start-system (test-system))]
    (try
      (is (= {:data {:gameById {:name       "Zertz"
                                :summary    "Two player abstract with forced moves and shrinking board"
                                :maxPlayers 2
                                :minPlayers 2
                                :playTime   nil}}}
            (q system
              "{ gameById(id: 1234) { name summary minPlayers maxPlayers playTime }}"
              nil)))
      (finally
        (component/stop-system system)))))
