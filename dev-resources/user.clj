(ns user
  (:require
    [criterium.core :as c]
    [clojure.java.io :as io]
    com.walmartlabs.lacinia.expound
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia :as l]
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.trace :refer [set-compile-trace!
                                           set-enable-trace!]]
    [expound.alpha :as expound]))

(require 'com.walmartlabs.lacinia.expound)

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(comment
  (do
    (set! *warn-on-reflection* true)
    (set-compile-trace! true)
    (set-enable-trace! true))`

  (set-enable-trace! false)

  )

