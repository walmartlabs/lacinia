; Copyright (c) 2017-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns com.walmartlabs.lacinia.parser.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :refer [resource]]
            [com.walmartlabs.test-utils :refer [execute]]
            [com.walmartlabs.lacinia.parser.schema :as parser]
            [com.walmartlabs.lacinia.schema :as schema])
  (:import [clojure.lang ExceptionInfo]))

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
  (not= "Darth Vader" (get-in args [:character :name])))

(defn ^:private new-character
  [ctx args _]
  (fn [] nil))

(def ^:private resolver-map {:Query {:in_episode in-episode}
                             :OtherQuery {:find_by_names find-by-names}
                             :Mutation {:add add}})

(def ^:private date-parse (schema/as-conformer str))

(def ^:private scalar-map {:Date {:parse date-parse :serialize date-parse}})

(def ^:private streamer-map {:Subscription {:new_character new-character}})

(defn ^:private parse-schema [path options]
  (-> path
      resource
      slurp
      (parser/parse-schema options)))

(defn ^:private parse-string
  [s]
  (parser/parse-schema s {}))

;; Warm up with some very targeted parser tests
;; (these are invaluable after any large changes to the grammar).

(deftest schema-scalar
  (is (= {:scalars
          {:Date {}}}
         (parse-string "scalar Date"))))

(deftest schema-input-type
  (is (= {:input-objects
          {:Ebb
           {:fields
            {:flow {:type 'String}}}}}
         (parse-string "input Ebb { flow: String }"))))

(deftest schema-type
  (is (= {:objects
          {:Ebb
           {:fields
            {:flow
             {:type 'String}}}}}
         (parse-string "type Ebb { flow: String }"))))

(deftest schema-enums
  (is (= {:enums
          {:Target
           {:values [{:enum-value :player}
                     {:enum-value :missile}
                     {:enum-value :treasure}]}}}
         (parse-string "enum Target { player missile treasure }"))))

(deftest schema-interface
  (is (= {:interfaces
          {:Flow
           {:fields
            {:ebb
             {:type 'String}}}}}
         (parse-string "interface Flow { ebb : String }"))))

(deftest schema-union
  (is (= {:unions
          {:Matter
           {:members [:Solid :Liquid :Gas :Plasma]}}}
         (parse-string "union Matter = Solid | Liquid | Gas | Plasma"))))

(deftest schema-field-args
  (is (= {:objects
          {:Ebb
           {:fields
            {:flow
             {:args {:enabled {:type 'Boolean}}
              :type 'String}}}}}
         (parse-string "type Ebb { flow (enabled: Boolean) : String }"))))


(deftest schema-directives
  (is (= '{:directive-defs
           {:Trace
            {:args
             {:label
              {:type (non-null String)}}
             :locations #{:field-definition :argument-definition}}}}
         (parse-string "directive @Trace (label : String!) on FIELD_DEFINITION | ARGUMENT_DEFINITION"))))

(deftest directive-args-must-be-unique
  (when-let [e (is (thrown-with-msg? Throwable #"Conflicting field argument"
                                     (parse-string "directive @Dupe (a : String, b : String, a : String) on ENUM")))]
    (is (= :a (-> e ex-data :key)))))

(deftest field-directive
  (is (= '{:directive-defs
           {:Trace
            {:args
             {:label
              {:type (non-null String)}}
             :locations #{:field-definition}}}
           :objects
           {:Ebb
            {:fields
             {:flow
              {:type String
               :directives [{:directive-type :Trace}]}
              :ready
              {:type Boolean
               :directives [{:directive-type :Trace
                             :directive-args {:label "flow-ready"}}]}}}}}
         (parse-string "directive @Trace (label : String!) on FIELD_DEFINITION
                        type Ebb { flow : String @Trace
                                   ready : Boolean @Trace(label: \"flow-ready\") }"))))


(deftest enum-directive
  (is (= {:enums {:Matter {:values [{:enum-value :Solid}
                                    {:enum-value :Liquid}
                                    {:enum-value :Gas}
                                    {:enum-value :Plasma}]
                           :directives [{:directive-type :Trace}]}}}
         (parse-string "enum Matter @Trace { Solid Liquid Gas Plasma}"))))

(deftest enum-value-directive
  (is (= {:enums {:Matter {:values [{:enum-value :Solid}
                                    {:enum-value :Liquid}
                                    {:enum-value :Gas}
                                    {:enum-value :Plasma
                                     :directives [{:directive-type :Rare}]}]}}}
         (parse-string "enum Matter { Solid Liquid Gas Plasma @Rare }"))))

(deftest input-object-directives
  (is (= '{:input-objects
           {:Ebb
            {:directives [{:directive-type :InputObject}]
             :fields {:flow {:type String
                             :args {:direction {:directives [{:directive-type :Arg}]
                                                :type String}}
                             :directives [{:directive-type :Field}]}}}}}
         (parse-string "input Ebb @InputObject { flow(direction : String @Arg) : String @Field }"))))

(deftest object-directives
  (is (= '{:objects
           {:Ebb
            {:directives [{:directive-type :Object}]
             :fields {:flow {:type String
                             :args {:direction {:directives [{:directive-type :Arg}]
                                                :type String}}
                             :directives [{:directive-type :Field}]}}}}}
         (parse-string "type Ebb @Object { flow(direction : String @Arg) : String @Field }"))))

(deftest interface-directives
  (is (= '{:interfaces
           {:Ebb
            {:directives [{:directive-type :Interface}]
             :fields {:flow {:type String
                             :args {:direction {:directives [{:directive-type :Arg}]
                                                :type String}}
                             :directives [{:directive-type :Field}]}}}}}
         (parse-string "interface Ebb @Interface { flow(direction : String @Arg) : String @Field }"))))

(deftest scalar-directives
  (is (= {:scalars {:Date {:directives [{:directive-type :deprecated}]}}}
         (parse-string "scalar Date @deprecated"))))

(deftest union-directives
  (is (= '{:objects
           {:Beauty
            {:fields {:level {:type String}}}}
           :unions
           {:Truth
            {:members [:Beauty]
             :directives [{:directive-type :Union}]}}}
         (parse-string "union Truth @Union = Beauty
                        type Beauty { level : String}"))))

(deftest field-argument-directive
  (is (= '{:objects
           {:Ebb
            {:fields
             {:flow {:type String
                     :args {:direction {:type String
                                        :directives [{:directive-type :Trace}]}
                            :level {:type Int
                                    :default-value 10
                                    :directives [{:directive-type :Flow}]}}}}}}}
         (parse-string "type Ebb { flow(direction: String @Trace, level : Int = 10 @Flow) : String }"))))

(deftest schema-directives
  (is (= {:roots {:query :Query}
          :directives [{:directive-type :Schema}]}
         (parse-string "schema @Schema { query : Query }"))))

(deftest schema-parsing
  (let [parsed-schema (parse-schema "sample_schema.sdl"
                                    {:resolvers resolver-map
                                     :scalars scalar-map
                                     :streamers streamer-map
                                     :documentation {:Character "A character"
                                                     :Character/name "Character name"
                                                     :Character/birthDate "Date of Birth"
                                                     :Query/in_episode "Find all characters for a given episode"
                                                     :Query/in_episode.episode "Episode for which to find characters"}})]
    (testing "parsing"
      (is (= {:directive-defs {:Trace {:args {:label {:type '(non-null String)}}
                                       :description "Extra tracing of field operations"
                                       :locations #{:field-definition}}}
              :enums {:episode {:values [{:enum-value :NEWHOPE}
                                         {:enum-value :EMPIRE}
                                         {:enum-value :JEDI}]}}
              ;; Demonstrate that the scalar in the SDL (with a description) has the :parse and :serialize merged onto it:
              :scalars {:Date {:parse date-parse
                               :serialize date-parse
                               :description "Date in standard ISO format"}}
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
                                                                       :default-value :NEWHOPE
                                                                       :description "Episode for which to find characters"}}
                                                      :directives [{:directive-type :Trace}]
                                                      :resolve in-episode
                                                      :description "Find all characters for a given episode"
                                                      :type '(list :CharacterOutput)}}}
                        :OtherQuery {:fields {:find_by_names {:args {:names {:type '(non-null (list (non-null String)))}}
                                                              :resolve find-by-names
                                                              :type '(list :CharacterOutput)}}}
                        :Mutation {:fields {:add {:args {:character {:type :Character
                                                                     :default-value {:name "Unspecified"
                                                                                     :episodes [:NEWHOPE :EMPIRE :JEDI]}}}
                                                  :directives [{:directive-type :deprecated
                                                                :directive-args {:reason "just for testing"}}
                                                               {:directive-type :Trace
                                                                :directive-args {:label "add-character"}}]
                                                  :resolve add
                                                  :type 'Boolean}}}
                        :Subscription {:fields {:new_character {:args {:episodes {:type '(non-null (list (non-null :episode)))}}
                                                                :stream new-character
                                                                :type :CharacterOutput}}}}
              :roots {:mutation :Mutation
                      :query :Queries
                      :subscription :Subscription}}
             parsed-schema)))
    (testing "using parsed schema"
      (let [compiled (schema/compile parsed-schema)]
        (is (= {:data {:in_episode [{:name "Jabba the Hutt"
                                     :birthDate "?"
                                     :episodes [:JEDI]}]
                       :find_by_names [{:name "Horace the Demogorgon" :episodes []}]}}
               (execute compiled "query { in_episode(episode: JEDI) { name birthDate episodes} find_by_names(names: [\"Horace the Demogorgon\"]) {name episodes} }" nil {})))
        (is (= {:data {:add false}}
               (execute compiled "mutation { add(character: {name: \"Darth Vader\"}) }" nil {})))))))

(deftest can-identify-unknown-doc
  (when-let [e (is (thrown-with-msg? ExceptionInfo
                                     #"Error attaching documentation: type not found"
                                     (parse-schema "interfaces.sdl" {:documentation {:Agent "Virtual killers."}})))]
    (is (= {:type-name :Agent}
           (ex-data e)))))

(deftest can-attach-doc-to-interfaces
  (is (= '{:interfaces
           {:Matrix
            {:description "A virtual construct."
             :fields
             {:eject {:args {:the_one {:description "If true, then Neo is ejected."
                                       :type (non-null Boolean)}}
                      :description "Eject a Human into Zen."
                      :type :Human}}}}
           :objects
           {:Human
            {:description "Power source."
             :fields
             {:name {:description "Unimportant."
                     :type (non-null String)}}}}}
         (parse-schema "interfaces.sdl"
                       {:documentation
                        {:Matrix "A virtual construct."
                         :Matrix/eject "Eject a Human into Zen."
                         :Matrix/eject.the_one "If true, then Neo is ejected."
                         :Human "Power source."
                         :Human/name "Unimportant."}}))))

(deftest can-attach-doc-to-unions
  (is (= '{:objects {:Agent {:fields {:alias {:type String}
                                      :id {:type Int}}}
                     :Human {:fields {:name {:type String}}}}
           :unions {:Combatant {:description "Being in the Matrix."
                                :members [:Human
                                          :Agent]}}}
         (parse-schema "unions.sdl"
                       {:documentation
                        {:Combatant "Being in the Matrix."}}))))

(deftest can-not-attach-doc-to-union-member
  (when-let [e (is (thrown-with-msg? ExceptionInfo
                                     #"Error attaching documentation: union members may not be documented"
                                     (parse-schema "unions.sdl" {:documentation {:Combatant/Human "Energy Source."}})))]
    (is (= {:type-name :Combatant}
           (ex-data e)))))

(deftest can-attach-doc-to-enums
  (is (= {:enums {:Location {:description "Where a scene takes place."
                             :values [{:description "The virtual world."
                                       :enum-value :MATRIX}
                                      {:description "The apparently real world outside the Matrix."
                                       :enum-value :ZION}
                                      {:description "Where the 'bots hang out."
                                       :enum-value :MACHINE_CITY}]}}}
         (parse-schema "enums.sdl" {:documentation
                                    {:Location "Where a scene takes place."
                                     :Location/MATRIX "The virtual world."
                                     :Location/ZION "The apparently real world outside the Matrix."
                                     :Location/MACHINE_CITY "Where the 'bots hang out."}}))))

(deftest can-not-attach-doc-to-enum-value-args
  (when-let [e (is (thrown-with-msg? ExceptionInfo
                                     #"Error attaching documentation: enum values do not contain fields"
                                     (parse-schema "enums.sdl" {:documentation {:Location/MATRIX.highway "Dangerous."}})))]
    (is (= {:type-name :Location}
           (ex-data e)))))

(deftest enum-value-must-exist
  (when-let [e (is (thrown-with-msg? ExceptionInfo
                                     #"Error attaching documentation: enum value not found"
                                     (parse-schema "enums.sdl" {:documentation {:Location/FLOOR_13 "Similar."}})))]
    (is (= {:type-name :Location
            :enum-value :FLOOR_13}
           (ex-data e)))))

(deftest schema-validation
  (when-let [e (is (thrown? ExceptionInfo
                            (parse-schema "bad_schema.sdl" {})))]
    (is (= "Conflicting field argument: `episode'."
           (.getMessage e)))
    (is (= {:key :episode
            :locations [{:column 14
                         :line 28}
                        {:column 42
                         :line 28}]}
           (ex-data e))))

  ;; This is a stand-in for any of the root things that can have a key conflict
  (when-let [e (is (thrown? ExceptionInfo
                            (parse-schema "duplicate-type.sdl" {})))]
    (is (= "Conflicting objects: `Tree'." (.getMessage e)))
    (is (= {:key :Tree
            :locations [{:column 1
                         :line 1}
                        {:column 1
                         :line 5}]}
           (ex-data e)))))

(deftest supports-multiple-inheritance
  (let [schema (-> "mult-inheritance.sdl"
                   resource
                   slurp
                   (parser/parse-schema {:resolvers {:Query {:me (constantly {:id "101"
                                                                              :ip "127.0.0.1"})}}})
                   schema/compile)
        result (execute schema "{ me { id ip } }")]
    (is (= {:data {:me {:id "101"
                        :ip "127.0.0.1"}}}
           result))))

(deftest block-strings
  (let [schema (-> "blockquote.sdl"
                   resource
                   slurp
                   (parser/parse-schema {}))
        input-arg (get-in schema [:objects :Query :fields :with_default :args :arg])]
    (is (some? input-arg))
    (is (= "line 1\n\nline 3\n  indented line 4\nline 5"
           (:default-value input-arg)))))

(deftest embedded-docs
  (let [schema (-> "documented-schema.sdl"
                   resource
                   slurp
                   (parser/parse-schema {}))]
    (is (= {:enums {:FileNodeType {:description "File node type."
                                   :values [{:description "A standard file-system file."
                                             :enum-value :FILE}
                                            {:description "A directory that may contain other files and directories."
                                             :enum-value :DIR}
                                            {:description "A special file, such as a device."
                                             :enum-value :SPECIAL}]}}
            :interfaces {:Named {:description "Things that have a name."
                                 :fields {:name {:description "The unique name for the Named thing."
                                                 :type 'String}}}}
            :objects {:Directory {:description "Directory type."
                                  :fields {:contents {:args {:match {:description "Wildcard used for matching."
                                                                     :type 'String}}
                                                      :type '(list :DirectoryListing)}
                                           :name {:type 'String}
                                           :permissions {:type :Permissions}}
                                  :implements [:Named]}
                      :DirectoryListing {:fields {:name {:type 'String}
                                                  :node_type {:type :FileNodeType}}
                                         :implements [:Named]}
                      :File {:fields {:name {:type 'String}}
                             :implements [:Named]}
                      :Query {:fields {:file {:args {:path {:type 'String}}
                                              :type :FileSystemEntry}}}}
            :scalars {:Permissions {:description "String that identifies permissions on a file or directory."}}
            :unions {:FileSystemEntry {:description "Stuff that can appear on the file system"
                                       :members [:File
                                                 :Directory]}}}
           schema))))
