(ns user
  (:require
    [criterium.core :as c]
    [clojure.java.io :as io]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia :as l]
    [clojure.spec.alpha :as s]
    [expound.alpha :as expound]))

(comment

  ;; Currently, this breaks a couple of tests, so it should only be
  ;; invoked for development.
  (alter-var-root #'s/*explain-out* (constantly expound/printer))

  )
