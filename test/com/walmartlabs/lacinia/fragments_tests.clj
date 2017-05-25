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
           }}"))))

(deftest inline-fragments-merged
  (is (= {:data {:characters [{:name "R2-D2"
                               :power "AC"
                               :id nil}
                              {:name "Luke"
                               :home_world "Tatooine"}]}}
         (q "{ characters {
               name
               ... on droid { power }
               ... on human { home_world }
               ... on droid { id }
             }}"))))

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
