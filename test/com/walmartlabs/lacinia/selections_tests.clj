(ns com.walmartlabs.lacinia.selections-tests
  (:require
   [clojure.test :refer [deftest is]]
   [com.walmartlabs.lacinia.parser :as parser]
   [com.walmartlabs.lacinia.executor :as executor]
   [com.walmartlabs.test-utils :refer [compile-schema execute]]
   [com.walmartlabs.lacinia.constants :as constants]
   [clojure.edn :as edn]
   [com.walmartlabs.test-schema :refer [test-schema]]
   [com.walmartlabs.lacinia.schema :as schema]))

(def default-schema
  (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver}))

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

(defn ^:private tree
  [query]
  (-> query
      app-context
      executor/selections-tree))

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

(deftest introspection-cases
  (is (= []
         (root-selections "{ hero { __typename }}")))
  (is (= [:character/name]
         (root-selections "{ hero { name __typename }}")))
  (is (= {:character/name nil}
         (tree "{ hero { name __typename }}"))))

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

(deftest basic-tree
  (is (= {:human/name nil}
         (tree "{ human { name }}")))

  (is (= {:droid/appears_in nil
          :droid/friends {:selections {:character/name nil}}
          :droid/name nil}
         (tree "{ droid { name appears_in friends { name }}}"))))

(deftest inline-fragments-are-flattened-in-tree
  (is (= {:character/name nil
          :droid/primary_function nil
          :human/friends {:selections {:character/name nil}}
          :human/homePlanet nil}
         (tree "{
              hero {
                name

                ... on human { homePlanet friends { name } }

                ... on droid { primary_function }
              }
            }"))))

(deftest named-fragments-are-flattened-in-tree
  (is (= {:character/name nil
          :character/friends {:selections {:character/name nil
                                           :droid/primary_function nil
                                           :human/homePlanet nil}}}
         (tree "query {
              hero {
              name
                friends {
                  name

                  ... ifHuman
                  ... ifDroid
                }
              }
            }

            fragment ifHuman on human { homePlanet }

            fragment ifDroid on droid { primary_function }
            "))))

(def ^:private selections-schema
  (compile-schema "selections-schema.edn"
                  {:root-resolve
                   (fn [context _ _]
                     {:tree (pr-str (executor/selections-tree context))
                      :detail "<detail>"})}))

(defn ^:private extract-tree
  [query vars]
  (-> (execute selections-schema query vars nil)
      (get-in [:data :root :tree])
      edn/read-string))

(deftest captures-arguments
  (is (= {:Root/detail {:args {:int_arg 5
                               :string_arg "frodo"}}
          :Root/tree nil}
         (extract-tree "{ root: get_root {
              tree
              detail(int_arg: 5, string_arg: \"frodo\")
            }
          }"
                       nil))))

(deftest captures-arguments-from-vars
  ;; Because the tree is prepared, the actual values are visible
  ;; even for variable-driven fields.
  (is (= {:Root/detail {:args {:int_arg 42
                               :string_arg "samwise"}}
          :Root/tree nil}
         (extract-tree "query($int_var: Int, $string_var: String) {
           root: get_root {
             tree
             detail(int_arg: $int_var, string_arg: $string_var)
           }
         }"
                       ;; Should be able to specify the int value as 42, not string
                       {:int_var "42"                       ; bug!
                        :string_var "samwise"}))))
