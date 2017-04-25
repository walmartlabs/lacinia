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
      (util/attach-resolvers {:hero db/resolve-hero
                              :human db/resolve-human
                              :droid db/resolve-droid
                              :friends db/resolve-friends})
      schema/compile))
