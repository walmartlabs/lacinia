(ns com.walmartlabs.lacinia.parser.schema-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :refer [resource]]
            [com.walmartlabs.lacinia.parser.schema :as parser]
            [com.walmartlabs.lacinia.schema :as schema]))

(deftest schema-parsing
  (let [date-parse (schema/as-conformer str)
        date-serialize (schema/as-conformer str)
        in-episode (partial identity)
        find-by-names (partial identity)
        add (partial identity)]
    (is (= {:enums {:episode {:values [:NEWHOPE :EMPIRE :JEDI]}}
            :scalars {:Date {:parse date-parse :serialize date-serialize}}
            :interfaces {:Human {:fields {:name {:type 'String}
                                          :birthDate {:type :Date}}}}
            :unions {:Queries {:members [:Query :OtherQuery]}}
            :objects {:Character {:description "A character"
                                  :fields {:name {:type '(non-null String)
                                                  :description "Character name"}
                                           :birthDate {:type :Date
                                                       :description "Date of Birth"}
                                           :episodes {:type '(list :episode)}}}
                      :CharacterOutput {:fields {:name {:type 'String}
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
                                {:Query {:in_episode in-episode}
                                 :OtherQuery {:find_by_names find-by-names}
                                 :Mutation {:add add}}
                                {:Date {:parse date-parse :serialize date-serialize}}
                                {:Character {:description "A character"
                                             :fields {:name "Character name"
                                                      :birthDate "Date of Birth"}}
                                 :Query {:fields {:in_episode "Find all characters for a given episode"}}})))))
