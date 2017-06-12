(ns com.walmartlabs.lacinia.fixed-types-test
  "In most cases, field resolvers return maps, but there's also support for returning fixed Java types that don't
  support metadata."
  (:require
    [clojure.test :refer [deftest is]]
    [com.walmartlabs.lacinia.schema :refer [tag-with-type]]
    [com.walmartlabs.test-utils :refer [compile-schema execute]]))

(defprotocol Human
  (^String getFirstName [this])
  (^String getLastName [this]))

(defprotocol Droid
  (^String getDesignation [this])
  (^String getFunction [this]))

(deftype HumanImpl [first-name last-name]

  Human
  (getFirstName [_] first-name)
  (getLastName [_] last-name))

(deftype DroidImpl [designation function]

  Droid
  (getDesignation [_] designation)
  (getFunction [_] function))

(def ^:private luke (->HumanImpl "Luke" "Skywalker"))
(def ^:private c3p0 (->DroidImpl "C3P0" "protocol"))
;; This executes some logic for redundant tagging: Sidekick gets replaced
;; with the proper value, :Droid.
(def ^:private r2d2 (tag-with-type (->DroidImpl "R2D2" "astromech") :Sidekick))

(defn ^:private prop-resolver
  [prop-name]
  (fn [_ _ value]
    ;; This is a very inefficient way to do this, suitable just for
    ;; testing!
    (-> value bean (get prop-name))))

(def ^:private schema
  (compile-schema "fixes-types-schema.edn"
                  {:prop prop-resolver
                   :resolve-human (constantly luke)
                   :resolve-droid (constantly c3p0)
                   :resolve-astromech (constantly r2d2)
                   :resolve-characters (constantly
                                         [(tag-with-type luke :Human)
                                          (tag-with-type c3p0 :Droid)])}))

(deftest tag-not-needed
  (is (= {:data {:c3p0 {:designation "C3P0"
                        :function "protocol"}
                 :r2d2 {:designation "R2D2"
                        :function "astromech"}
                 :luke {:first_name "Luke"
                        :last_name "Skywalker"}}}
         (execute schema "
{
  luke: human { first_name last_name }
  c3p0: droid { designation function }
  r2d2: astromech { designation function }
}"))))

(deftest tag-needed-inside-union
  (is (= {:data {:characters [{:first_name "Luke"}
                              {:designation "C3P0"}]}}
         (execute schema "
{
  characters {
    ... on Human { first_name }
    ... on Droid { designation }
  }
}"))))


