(ns com.walmartlabs.lacinia.parser-test
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.test-schema :refer [test-schema]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.parser :as parser]))

(def ^:private compiled-schema
  (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver}))

(defn ^:private ops
  [query]
  (->> query
       (parser/parse-query compiled-schema)
       (parser/operations)))

(deftest single-query
  (is (= {:operations #{:hero}
          :type :query}
         (ops "{ hero { name }}"))))

(deftest multiple-operations
  (is (= {:operations #{:hero
                        :human}
          :type :query}
         (ops "{ luke: hero { name }
                 leia: human { name }}"))))

(deftest mutations
  (is (= {:operations #{:changeHeroHomePlanet}
          :type :mutation}
         (ops "mutation { changeHeroHomePlanet(id: \"1234\", newHomePlanet: \"Gallifrey\") {
               name
             }
           }"))))
