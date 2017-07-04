(ns com.walmartlabs.lacinia.merge-selections-test
  "Queries may include the same fields (with or without aliases) repeated multiple times and they should merge together.

  Earlier versions of Lacinia would tend to overwrite the earlier fields with the later ones."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-utils :refer [execute]]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia.schema :as schema]))

(def default-schema
  (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver}))

(defn ^:private q
  ([query-string]
   (q query-string nil))
  ([query-string vars]
   (execute default-schema query-string vars nil)))

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

(deftest nested-merge-with-vars
  (is (= {:data {:human {:friends [{:forceSide {:name "light"}
                                    :name "Han Solo"}
                                   {:forceSide {:name "light"}
                                    :name "Leia Organa"}
                                   {:forceSide nil
                                    :name "C-3PO"}
                                   {:forceSide nil
                                    :name "R2-D2"}]
                         :name "Luke Skywalker"}}}
         (q "
query ($who : String) {
  human(id: $who) {
    name

    friends {
      name
    }
  }

  human (id: $who) {
    friends {
      forceSide { name }
    }
  }
}" {:who "1000"}))))

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
