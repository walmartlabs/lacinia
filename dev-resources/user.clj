(ns user
  (:require
    [criterium.core :as c]
    [clojure.java.io :as io]
    com.walmartlabs.lacinia.expound
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia :as l]
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.trace :as trace]
    [expound.alpha :as expound]))

(require 'com.walmartlabs.lacinia.expound)

(alter-var-root #'s/*explain-out* (constantly expound/printer))

(comment
  (do
    (set! *warn-on-reflection* true)
    (trace/set-compile-trace! true)
    (trace/set-enable-trace! true)
    (add-tap clojure.pprint/pprint)
    (trace/trace :msg "Tracing is enabled"))

  (trace/set-enable-trace! false)

  )

