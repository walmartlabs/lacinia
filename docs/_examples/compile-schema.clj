(ns org.example.schema
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.util :as util]
    [org.example.db :as db]))

(defn star-wars-schema
  []
  (-> (io/resource "star-wars-schema.edn")
      slurp
      edn/read-string
      (util/inject-resolvers {:Query/hero db/resolve-hero
                              :Query/human db/resolve-human
                              :Query/droid db/resolve-droid
                              :Human/friends db/resolve-friends
                              :Droid/friends db/resolve-friends})
      schema/compile))

