(ns org.example.schema
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [com.walmartlabs.lacinia.schema :as schema]
    [org.example.db :as db]))


(defn star-wars-schema
  []
  (-> (io/resource "star-wars-schema.edn")
      slurp
      edn/read-string
      (assoc-in [:queries :hero :resolve] db/resolve-hero)
      (assoc-in [:queries :human :resolve] db/resolve-human)
      (assoc-in [:queries :droid :resolve] db/resolve-droid)
      (assoc-in [:objects :human :fields :friends :resolve] db/resolve-friends)
      (assoc-in [:objects :droid :fields :friends :resolve] db/resolve-friends)
      schema/compile))
