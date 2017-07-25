(ns com.walmartlabs.test-schema
  (:require [com.walmartlabs.lacinia.schema :as schema ]
            [com.walmartlabs.lacinia.resolve :refer [resolve-as]]
            [com.walmartlabs.lacinia.internal-utils :refer [ map-vals]]
            [clojure.spec.alpha :as s]))

;; —————————————————————————————————————————————————————————————————————————————
;; ## Helpers

(defn ^:private hash-map-by
  "Constructs a hash map from the supplied values.

  key-fn
  : Passed a value and extracts the key for that value.

  values
  : Seq of values from which map keys and values will be extracted."
  [key-fn values]
  (into {} (map (juxt key-fn identity)) values))

(def humans-data
  (->> [{:id         "1000"
         :name       "Luke Skywalker"
         :friends    ["1002", "1003", "2000", "2001"]
         :enemies    ["1002"]
         :appears-in [:NEWHOPE :EMPIRE :JEDI]
         :homePlanet "Tatooine"
         :force-side  "3001"}
        {:id         "1001"
         :name       "Darth Vader"
         :friends    ["1004"]
         :enemes     ["1000"]
         :appears-in [:NEWHOPE :EMPIRE :JEDI]
         :homePlanet "Tatooine"
         :force-side  "3000"}
        {:id         "1003"
         :name       "Leia Organa"
         :friends    ["1000", "1002", "2000", "2001"]
         :enemies    ["1001"]
         :appears-in [:NEWHOPE :EMPIRE :JEDI]
         :homePlanet "Alderaan"
         :force-side  "3001"}
        {:id         "1002"
         :name       "Han Solo"
         :friends    ["1000", "1003", "2001"]
         :enemies    ["1001"]
         :appears-in [:NEWHOPE :EMPIRE :JEDI]
         :force-side  "3001"}
        {:id         "1004"
         :name       "Wilhuff Tarkin"
         :friends    ["1001"]
         :enemies    ["1000"]
         :appears-in [:NEWHOPE]
         :force-side  "3000"}
        ]
       (hash-map-by :id)
       (map-vals #(assoc % ::type :human))))

(def droids-data
  (->> [{:id               "2001"
         :name             "R2-D2"
         :friends          ["1000", "1002", "1003"]
         :appears-in       [:NEWHOPE :EMPIRE :JEDI]
         :primary-function "ASTROMECH"}
        {:id               "2000"
         :name             "C-3PO"
         :friends          ["1000", "1002", "1003", "2001"]
         :appears-in       [:NEWHOPE :EMPIRE :JEDI]
         :primary-function "PROTOCOL"}
        ]
       (hash-map-by :id)
       (map-vals #(assoc % ::type :droid))))

(def force-data
  (->> [{:id "3000"
         :name "dark"
         :members ["1001" "1004"]}
        {:id "3001"
         :name "light"
         :members ["1000" "1003" "1002"]}]
       (hash-map-by :id)
       (map-vals #(assoc % ::type :force))))

(defn with-tag
  [v]
  (if-let [type (::type v)]
    (schema/tag-with-type v type)
    v))

(defn get-character
  "Gets a character."
  [k]
  (with-tag (or (get humans-data k)
                (get droids-data k))))

(defn find-by-name
  [n]
  (->> (concat (vals humans-data) (vals droids-data))
       (filter #(= n (:name %)))
       first))

(defn get-friends
  "Gets the friends of a character."
  [friends-coll]
  (mapv get-character friends-coll))

(defn get-enemies
  "Gets the enemie of a character."
  [enemies-coll]
  (mapv get-character enemies-coll))

(defn get-force-data
  "Gets side of the force that character is on."
  [k]
  (with-tag (get force-data k)))

(defn get-force-members
  "Gets the members of a force side."
  [force-id]
  (let [members (get force-data force-id)]
    (mapv get-character members)))

(defn get-hero
  "Usually retrieves the undisputed hero of the Star Wars trilogy, R2-D2."
  [episode]
  (get-character (if (= :NEWHOPE episode)
                   "1000"                                   ; luke
                   "2001"                                   ; r2d2
                   )))

(defn get-villain
  "Retrieves the biggest villain of the episode."
  [episode]
  (get-character (condp = episode
                   :NEWHOPE "1004" ;; Tarkin
                   :EMPIRE "1001" ;; Vader
                   :JEDI "1001"
                   nil)))

;; —————————————————————————————————————————————————————————————————————————————
;; ## Schema

(def date-formatter
  "Used by custom scalar :Date"
  (java.text.SimpleDateFormat. "yyyy-MM-dd"))

(def test-schema
  {:enums
   {:episode
    {:description "The episodes of the original Star Wars trilogy."
     :values [:NEWHOPE :EMPIRE :JEDI]}}

   :scalars
   {:Date
    {:parse (s/conformer #(.parse date-formatter %))
     :serialize (s/conformer (constantly "A long time ago"))}}

   :interfaces
   {:character
    {:fields {:id {:type 'String}
              :name {:type 'String}
              :appears_in {:type '(list :episode)}
              :friends {:type '(list :character)}
              :enemies {:type '(list (non-null :character))}
              :family {:type '(non-null (list :character))}
              :droids {:type '(non-null (list (non-null :character)))}
              :forceSide {:type :force}
              :foo {:type '(non-null String)}
              :bar {:type :character}
              :best_friend {:type :character}
              :arch_enemy {:type '(non-null :character)}}}}

   :input-objects
   {:nestedInputObject ;; used for testing argument coercion and validation
    {:fields {:integerArray {:type '(list Int)}
              :name {:type 'String}
              :date {:type :Date}}}

    :testInputObject ;; used for testing argument coercion and validation
    {:fields {:integer {:type 'Int}
              :string {:type 'String}
              :nestedInputObject {:type :nestedInputObject}}}}

   :objects
   {:force
    {:fields {:id {:type 'String}
              :name {:type 'String}
              :members {:type '(list :character)
                        :resolve (fn [ctx args v]
                                   (let [{:keys [members]} v]
                                     (get-force-members members)))}}}

    ;; used for testing argument coercion and validation
    :echoArgs
    {:fields {:integer {:type 'Int}
              :integerArray {:type '(list Int)}
              :inputObject {:type :testInputObject}}}

    :galaxy_date
    {:fields {:date {:type :Date}}}

    :droid
    {:implements [:character]
     :fields {:id {:type 'String}
              :name {:type 'String}
              :appears_in {:type '(list :episode)}
              :friends {:type '(list :character)
                        :resolve (fn [ctx args v]
                                   (let [{:keys [friends]} v]
                                     (get-friends friends)))}
              :enemies {:type '(list (non-null :character))
                        :resolve (fn [ctx args v]
                                   (let [{:keys [enemies]} v]
                                     (get-enemies enemies)))}
              :family {:type '(non-null (list :character))
                       :resolve (fn [ctx args v]
                                  (let [{:keys [friends]} v]
                                    (get-friends friends)))}
              :droids {:type '(non-null (list (non-null :character)))
                       :resolve (fn [ctx args v] [])}
              :forceSide {:type :force
                          :resolve (fn [ctx args v]
                                     (let [{:keys [force-side]} v]
                                       (get-force-data force-side)))}
              :foo {:type '(non-null String)}
              :bar {:type :character}
              :best_friend {:type :character
                            :resolve (fn [ctx args v]
                                       (let [{:keys [friends]} v]
                                         (first (get-friends friends))))}

              :arch_enemy {:type '(non-null :character)
                           :resolve (fn [ctx args v]
                                      nil)}
              :primary_function {:type '(list String)}
              :incept_date {:type 'Int
                            ;; This will cause a failure, since expecting single:
                            :resolve (fn [_ _ _]
                                       [1 2 3])}
              :accessories {:type '(list String)
                            ;; This will cause a failure, since expecting multiple:
                            :resolve (fn [_ _ _]
                                       "<Single Value>")}}}

    :villain
    {:implements [:character]
     :fields {:id {:type 'String}
              :name {:type 'String}
              :appears_in {:type '(list :episode)}
              :friends {:type '(list :character)
                        :resolve (fn [ctx args v]
                                   (let [{:keys [friends]} v]
                                     (get-friends friends)))}
              :enemies {:type '(list (non-null :character))
                        :resolve (fn [ctx args v]
                                   (let [{:keys [enemies]} v]
                                     (get-enemies enemies)))}
              :family {:type '(non-null (list :character))
                       :resolve (fn [ctx args v]
                                  (let [{:keys [friends]} v]
                                    (get-friends friends)))}
              :droids {:type '(non-null (list (non-null :character)))
                       :resolve (fn [ctx args v]
                                  [])}
              :forceSide {:type :force
                          :resolve (fn [ctx args v]
                                     (let [{:keys [force-side]} v]
                                       (get-force-data force-side)))}
              :foo {:type '(non-null String)}
              :bar {:type :character}
              :best_friend {:type :character
                            :resolve (fn [ctx args v]
                                       (let [{:keys [friends]} v]
                                         (first (get-friends friends))))}

              :arch_enemy {:type '(non-null :character)
                           :resolve (fn [ctx args v]
                                      nil)}
              :primary_function {:type '(list String)}
              :homePlanet {:type 'String}}}

    :human
    {:implements [:character]
     :fields {:id {:type 'String}
              :name {:type 'String}
              :appears_in {:type '(list :episode)}
              :friends {:type '(list :character)
                        :resolve (fn [ctx args v]
                                   (let [{:keys [friends]} v]
                                     (get-friends friends)))}
              :enemies {:type '(list (non-null :character))
                        :resolve (fn [ctx args v]
                                   (let [{:keys [enemies]} v]
                                     (get-enemies enemies)))}
              :family {:type '(non-null (list :character))
                       :resolve (fn [ctx args v]
                                  (let [{:keys [friends]} v]
                                    (get-friends friends)))}
              :droids {:type '(non-null (list (non-null :character)))
                       :resolve (fn [ctx args v]
                                  [])}
              :forceSide {:type :force
                          :resolve (fn [ctx args v]
                                     (let [{:keys [force-side]} v]
                                       (get-force-data force-side)))}
              :foo {:type '(non-null String)}
              :bar {:type :character}
              :best_friend {:type :character
                            :resolve (fn [ctx args v]
                                       (let [{:keys [friends]} v]
                                         (first (get-friends friends))))}

              :arch_enemy {:type '(non-null :character)
                           :resolve (fn [ctx args v]
                                      nil)}
              :primary_function {:type '(list String)}
              :homePlanet {:type 'String}}}}

   :mutations
   {:changeHeroName {:type :character
                     :args {:from {:type 'String} :to {:type 'String
                                                       :default-value "Rey"}}
                     :resolve (fn [ctx args v]
                                (let [{:keys [from to]} args]
                                  (-> (find-by-name from)
                                      (assoc :name to)
                                      with-tag)))}
    :addHeroEpisodes {:type :character
                      :args {:id {:type '(non-null String)}
                             :episodes {:type '(non-null (list :episode))}
                             :does_nothing {:type 'String}}
                      :resolve (fn [ctx args v]
                                 (with-tag
                                   (let [{:keys [id episodes]} args
                                         hero (get humans-data id)]
                                     (update hero :appears-in concat episodes))))}
    :changeHeroHomePlanet {:type :human
                           :args {:id {:type '(non-null String)}
                                  :newHomePlanet {:type 'String}}
                           :resolve (fn [ctx args v]
                                      (with-tag
                                        (let [hero (get humans-data (:id args))]
                                          (if (contains? args :newHomePlanet)
                                            (assoc hero :homePlanet (:newHomePlanet args))
                                            hero))))}}

   :queries
   {:hero {:type '(non-null :character)
           :args {:episode {:type :episode}}
           :resolve (fn [ctx args v]
                      (when (contains? args :episode)
                        (get-hero (:episode args))))}
    :echoArgs {:type :echoArgs
               :args {:integer {:type 'Int}
                      :integerArray {:type '(list Int)}
                      :inputObject {:type :testInputObject}}
               :resolve (fn [ctx args v]
                          args)}
    :now {:type :galaxy_date
          :resolve (fn [ctx args v]
                     {:date (java.util.Date.)})}
    :human {:type '(non-null :human)
            :args {:id {:type 'String
                        :default-value "1001"}}
            :resolve (fn [ctx args v]
                       (let [{:keys [id]} args]
                         (get humans-data id)))}
    :droid {:type :droid
            :args {:id {:type 'String
                        :default-value "2001"}}
            :resolve (fn [ctx args v]
                       (get droids-data (:id args)))}
    :villain {:type :villain
              :args {:episode {:type :episode}}
              :resolve (fn [ctx args v]
                         (get-villain (:episode args)))}}})
