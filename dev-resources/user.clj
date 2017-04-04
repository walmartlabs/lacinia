(ns user
  #_(:require [criterium.core :as c]
            [com.walmartlabs.lacinia :as g]
            [org.example.schema :refer [star-wars-schema]]))

#_(def schema (star-wars-schema))

#_(defn q
  ([query]
    (q query nil))
  ([query vars]
   (g/execute schema query vars nil)))
