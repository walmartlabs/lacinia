(ns com.walmartlabs.lacinia.selections-tests
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.parser :as parser]
    [com.walmartlabs.lacinia.executor :as executor]
    [com.walmartlabs.test-utils :refer [default-schema]]
    [com.walmartlabs.lacinia.constants :as constants]))

(defn ^:private app-context
  [query]
  (let [parsed-query (parser/parse-query default-schema query)]
    {constants/parsed-query-key parsed-query
     constants/selection-key (-> parsed-query :selections first)}))


(defn ^:private root-selections
  [query]
  (-> query
      app-context
      executor/selections-seq))

(deftest simple-cases
  (is (= [:character/name]
         (root-selections "{ hero { name }}")))

  (is (= [:human/name :human/homePlanet]
         (root-selections "{ human { name homePlanet }}")))

  (is (= [:human/name :human/friends :human/enemies
          :character/name                                   ; friends
          :character/appears_in
          :character/name]                                  ; enemies
         (root-selections "{ human { name friends { name appears_in } enemies { name }}}"))))

(deftest mutations
  (is (= [:human/name :human/homePlanet]
         (root-selections "mutation { changeHeroHomePlanet (id: \"123\",
            newHomePlanet: \"Venus\") {
            name homePlanet
          }
         }"))))

(deftest inline-fragments
  (is (= [:character/name
          :human/homePlanet
          :droid/primary_function]
         (root-selections
           "{
              hero {
                name

                ... on human { homePlanet }

                ... on droid { primary_function }
              }
            }"))))

(deftest named-fragments
  (is (= [:character/name
          :human/homePlanet
          :droid/primary_function]
         (root-selections
           "query {
              hero {
                name

                ... ifHuman
                ... ifDroid
              }
            }

            fragment ifHuman on human { homePlanet }

            fragment ifDroid on droid { primary_function }
            "))))

(deftest is-selected
  (let [context (app-context "{ human { name homePlanet }}")]
    (is (executor/selects-field? context
                                 :human/name))
    (is (executor/selects-field? context :human/homePlanet))

    (is (not (executor/selects-field? context :character/name)))))
