(ns com.walmartlabs.lacinia.merge-selections-test
  "Tests for merging of selections."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [default-schema execute]]))

(defn ^:private q
  [query-string]
  (execute default-schema query-string nil nil))

(deftest immediate-field-merge
  ;; Default for human is 1001 - Darth Vader
  (is (= {:data {:human {:name "Darth Vader"
                         :homePlanet "Tatooine"}}}
         (q "
{
  human { name }
  human { homePlanet }
}"))))

(deftest understands-aliases
  ;; Default for human is 1001 - Darth Vader
  (is (= {:data {:darth {:name "Darth Vader"
                         :homePlanet "Tatooine"}}}
         (q "
{
  darth: human { name }
  darth: human { homePlanet }
}"))))

(deftest nested-merge
  (is (= {:data {:human {:friends [{:forceSide {:id "3000"
                                                :name "dark"}
                                    :name "Wilhuff Tarkin"}]
                         :name "Darth Vader"}}}
         (q "
{
  human {
    name

    friends {
      name
    }
  }

  human {
    friends {
      forceSide { id name }
    }
  }
}"))))

(deftest conflicting-arguments-are-identified
  (is (= {:errors [{:arguments nil
                    :field-name :human
                    :incompatible-arguments {:id "1000"}
                    :message "Different selections of field `human' of type `QueryRoot' have incompatible arguments. Use alias names if this is intentional."
                    :object-name :QueryRoot}]}
         (q "
{
  human {
    name

    friends {
      name
    }
  }

  human(id: \"1000\") {
    friends {
      forceSide { id name }
    }
  }
}"))))

(deftest fragments-merge-into-selection
  (is (= {:data {:luke {:friends [{:forceSide {:id "3001"
                                               :name "light"}
                                   :homePlanet nil
                                   :name "Han Solo"}
                                  {:forceSide {:id "3001"
                                               :name "light"}
                                   :homePlanet "Alderaan"
                                   :name "Leia Organa"}
                                  {:forceSide nil
                                   :name "C-3PO"}
                                  {:forceSide nil
                                   :name "R2-D2"}]
                        :name "Luke Skywalker"}}}
         (q "
{
  luke: human(id: \"1000\") {
    name

    friends {
      name
    }
  }

  luke: human(id: \"1000\") {
    friends {
      ... on human { homePlanet }
      forceSide { id name }
    }
  }
}"))))
