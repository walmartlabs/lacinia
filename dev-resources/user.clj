(ns user
  (:require
    [criterium.core :as c]
    [clojure.java.io :as io]
    com.walmartlabs.lacinia.expound
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia :as l]
    [clojure.spec.alpha :as s]
    [net.lewisship.trace :as trace]
    [expound.alpha :as expound]))

(require 'com.walmartlabs.lacinia.expound)

(alter-var-root #'s/*explain-out* (constantly expound/printer))


(defn trace-demo
  []
  (->> (range 10)
       (trace/trace>> :values %)
       (filter even?)
       (take 2)))

(comment
  (do
    (set! *warn-on-reflection* true)
    (trace/setup-default)
    (trace/trace :msg "Tracing is enabled"))

  (trace-demo)

  (trace/set-enable-trace! false)
  (trace/set-compile-trace! false)

  )

