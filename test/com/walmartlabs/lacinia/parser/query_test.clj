(ns com.walmartlabs.lacinia.parser.query-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.walmartlabs.test-utils :refer [compile-schema execute]]
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

(defn ^:private args
  [query]
  (->> query
       (parser/parse-query compiled-schema)
       (:selections)
       (first)
       (:arguments)))

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

(deftest string-value-escape-sequences
  (testing "ascii"
    (is (= {:id "1234"
            :newHomePlanet "A \"great\"\nplace\\	?"}
           (args "mutation { changeHeroHomePlanet(id: \"1234\", newHomePlanet: \"A \\\"great\\\"\\nplace\\\\\\t?\") {name}}"))))

  (testing "unicode"
    (is (= {:id "1138"
            :newHomePlanet "❄ＨＯＴＨ❄"}
           (args "mutation { changeHeroHomePlanet(id: \"1138\", newHomePlanet: \"\\u2744\\uff28\\uff2f\\uff34\\uff28\\u2744\") {name}}")))))

(deftest query-reserved-word
  ;; Use 'query', 'mutation', and 'subscription' in various unusual places.
  (let [schema (compile-schema "query-reserved.edn"
                               {:resolve-query (fn [_ args _]
                                                 args)})

        result (execute schema "{ query(mutation: true) { mutation }}")]
    (is (= {:data {:query {:mutation true}}}
           result))))

(deftest requires-compiled-schema
  (is (thrown-with-msg? IllegalStateException
                        #"The provided schema has not been compiled"
                        (execute {} "{ whatever }"))))
