(ns com.walmartlabs.lacinia.parser.schema-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :refer [resource]]
            [com.walmartlabs.lacinia.parser.schema :as parser]
            [com.walmartlabs.lacinia.schema :as schema]))

(defn ^:private in-episode
  [ctx args _]
  (let [luke {:name "Luke Skywalker" :birthDate "unknown" :episodes [:NEWHOPE :EMPIRE :JEDI]}
        leia {:name "Leia Organa" :birthDate "unknown" :episodes [:NEWHOPE :EMPIRE :JEDI]}
        han {:name "Han Solo" :birthDate "?" :episodes [:NEWHOPE :EMPIRE :JEDI]}
        boba-fett {:name "Boba Fett" :birthDate "NaN" :episodes [:EMPIRE :JEDI]}
        lando {:name "Lando Calrissian" :birthDate "?" :episodes [:EMPIRE :JEDI]}
        jabba {:name "Jabba the Hutt" :birthDate "?" :episodes [:JEDI]}]
    (case (:episode args)
      :NEWHOPE [luke han leia]
      :EMPIRE [boba-fett lando]
      :JEDI [jabba])))

(defn ^:private find-by-names
  [ctx args _]
  (map (fn [name] {:name name :birthDate "?" :episodes []})
       (:names args)))

(defn ^:private add
  [ctx args _]
  (if (= "Darth Vader" (get-in args [:character :name]))
    false
    true))

(def ^:private resolver-map {:Query {:in_episode in-episode}
                             :OtherQuery {:find_by_names find-by-names}
                             :Mutation {:add add}})

(def ^:private date-parse (schema/as-conformer str))

(def ^:private scalar-map {:Date {:parse date-parse :serialize date-parse}})

(deftest schema-parsing
  (is (= {:enums {:episode {:values [:NEWHOPE :EMPIRE :JEDI]}}
          :scalars {:Date {:parse date-parse :serialize date-parse}}
          :interfaces {:Human {:fields {:name {:type 'String}
                                        :birthDate {:type :Date}}}}
          :unions {:Queries {:members [:Query :OtherQuery]}}
          :input-objects {:Character {:description "A character"
                                      :fields {:name {:type '(non-null String)
                                                      :description "Character name"}
                                               :birthDate {:type :Date
                                                           :description "Date of Birth"}
                                               :episodes {:type '(list :episode)}}}}
          :objects {:CharacterOutput {:fields {:name {:type 'String}
                                               :birthDate {:type :Date}
                                               :episodes {:type '(list :episode)}}
                                      :implements [:Human]}
                    :Query {:fields {:in_episode {:args {:episode {:type :episode
                                                                   :defaultValue :NEWHOPE}}
                                                  :resolve in-episode
                                                  :description "Find all characters for a given episode"
                                                  :type '(list :CharacterOutput)}}}
                    :OtherQuery {:fields {:find_by_names {:args {:names {:type '(non-null (list (non-null String)))}}
                                                          :resolve find-by-names
                                                          :type '(list :CharacterOutput)}}}
                    :Mutation {:fields {:add {:args {:character {:type :Character
                                                                 :defaultValue {:name "Unspecified"
                                                                                :episodes [:NEWHOPE :EMPIRE :JEDI]}}}
                                              :resolve add
                                              :type 'Boolean}}}}
          :queries {:in_episode {:args {:episode {:type :episode
                                                  :defaultValue :NEWHOPE}}
                                 :description "Find all characters for a given episode"
                                 :resolve in-episode
                                 :type '(list :CharacterOutput)}
                    :find_by_names {:args {:names {:type '(non-null (list (non-null String)))}}
                                    :resolve find-by-names
                                    :type '(list :CharacterOutput)}}
          :mutations {:add {:args {:character {:type :Character
                                               :defaultValue {:name "Unspecified"
                                                              :episodes [:NEWHOPE :EMPIRE :JEDI]}}}
                            :resolve add
                            :type 'Boolean}}}
         (parser/parse-schema (slurp (resource "sample_schema.gql"))
                              resolver-map
                              scalar-map
                              {:Character {:description "A character"
                                           :fields {:name "Character name"
                                                    :birthDate "Date of Birth"}}
                               :Query {:fields {:in_episode "Find all characters for a given episode"}}}))))
