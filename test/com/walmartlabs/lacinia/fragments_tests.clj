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

(ns com.walmartlabs.lacinia.fragments-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia :refer [execute]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.test-utils :as utils]))

(defn ^:private resolve-characters
  [_ _ _]
  [(schema/tag-with-type {:name "R2-D2" :power "AC"} :droid)
   (schema/tag-with-type {:name "Luke" :home_world "Tatooine"} :human)])

(def ^:private schema
  (utils/compile-schema "fragments-schema.edn"
                        {:resolve-characters resolve-characters}))

(defn ^:private q [query]
  (utils/simplify (execute schema query nil nil)))

(deftest inline-fragments
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"}
                              {:name "Luke"
                               :home_world "Tatooine"}]}}
         (q "{ characters {
           name
           ... on droid { power }
           ... on human { home_world }
           }
           }"))))

(deftest query-root-fragment
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"}
                              {:name "Luke"
                               :home_world "Tatooine"}]}}
         (q "{ ... on Query {
           characters {
           name
           ... on droid { power }
           ... on human { home_world }
           }
           }
           }"))))

(deftest named-fragments
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"}
                              {:home_world "Tatooine"
                               :name "Luke"}]}}

         (q "query {

           characters {

             name

             ... droidFragment
             ... humanFragment
           }
         }

         fragment droidFragment on droid { power }
         fragment humanFragment on human { home_world }

         "))))

(deftest nested-fragments
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"}
                              {:home_world "Tatooine"
                               :name "Luke"}]}}

         (q "query {

           characters { ... characterFragment }

         }

         fragment characterFragment on character {

             name

             ... droidFragment
             ... humanFragment

         }

         fragment droidFragment on droid { power }
         fragment humanFragment on human { home_world }

         "))))

(deftest detect-simple-cycle
  (is (= {:errors [{:locations [{:column 16
                                 :line 9}]
                    :message "Fragment `droidFragment' is self-referential via field `friends', forming a cycle."}]}
         (q "
             query {
               characters { ... droidFragment }
             }

             fragment droidFragment on droid {
               name
               power
               friends { # line 9
                  ... droidFragment
               }
             }
             "))))

(deftest detect-cycle-via-named-fragment
  (is (= {:errors [{:locations [{:column 20
                                 :line 7}]
                    :message "Fragment `friendsFragment' is self-referential via named fragment `droidFragment', forming a cycle."}
                   {:locations [{:column 20
                                 :line 13}]
                    :message "Fragment `droidFragment' is self-referential via named fragment `friendsFragment', forming a cycle."}]}
         (q "
             query {
               characters { ... droidFragment }
             }

             fragment friendsFragment on character {
               ... droidFragment # line 7
             }

             fragment droidFragment on droid {
               name
               power
               ... friendsFragment # line 13
             }
             "))))

(deftest detect-cycle-via-named-fragment
  (is (= {:errors [{:locations [{:column 19
                                 :line 8}]
                    :message "Fragment `commonFragment' is self-referential via named fragment `friendsFragment', forming a cycle."}
                   {:locations [{:column 20
                                 :line 12}]
                    :message "Fragment `friendsFragment' is self-referential via named fragment `droidFragment', forming a cycle."}
                   {:locations [{:column 20
                                 :line 17}]
                    :message "Fragment `droidFragment' is self-referential via named fragment `commonFragment', forming a cycle."}]}
         (q "
             query {
               characters { ... droidFragment }
             }

             fragment commonFragment on character {
               name
               ...friendsFragment
             } # line 8

             fragment friendsFragment on character {
               ... droidFragment # line 7
             } # line 12

             fragment droidFragment on droid {
               power
               ... commonFragment
             } # line 17
             "))))

(deftest detect-cycle-via-inline-fragment
  (is (= {:errors [{:locations [{:column 23
                                 :line 8}]
                    :message "Fragment `commonFragment' is self-referential via inline fragment on type `droid', forming a cycle."}
                   {:locations [{:column 20
                                 :line 15}]
                    :message "Fragment `droidFragment' is self-referential via named fragment `commonFragment', forming a cycle."}]}
         (q "
             query {
               characters { ... droidFragment }
             }

             fragment commonFragment on character {
                name
               ... on droid { # line 8
                 ...droidFragment
               }
             }

             fragment droidFragment on droid {
               power
               ... commonFragment # line 15
             }
             "))))

(deftest fragment-on-undefined-type
  (is (= {:errors [{:message "Fragment `MovieCharacter' references unknown type `NotDefined'."
                    :extensions {:line 8 :column 19}}]}
         (q "
         query {
            characters {
              ... MovieCharacter
            }
         }

         fragment MovieCharacter on NotDefined {
            name
         }
         "))))