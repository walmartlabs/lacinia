(ns com.walmartlabs.lacinia.expound
  "Adds improved spec messages to Lacinia specs."
  {:since "0.26.0"}
  (:require
    [expound.alpha :refer [defmsg]]
    [com.walmartlabs.lacinia.schema :as schema]))


(defmsg ::schema/resolver-type "implement the com.walmartlabs.lacina.resolve/FieldResolver protocol")

(defmsg ::schema/wrapped-type "a wrapped type: '(list type) or '(non-null type)")

(defmsg ::schema/graphql-identifier "must be a valid GraphQL identifier: contain only letters, numbers, and underscores")

