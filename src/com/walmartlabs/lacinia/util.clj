(ns com.walmartlabs.lacinia.util
  "Useful utility functions."
  (:require
    clojure.walk
    [com.walmartlabs.lacinia.internal-utils
     :refer [to-message map-vals cond-let update?]]
    [clojure.string :as str]))

(defn ^:private attach-callbacks
  [field-container callbacks-map callback-kw error-name]
  (map-vals (fn [field]
              (cond-let
                :let [reference (get field callback-kw)]

                (nil? reference)
                field

                :let [factory? (not (keyword? reference))
                      callback-source (get callbacks-map
                                           (if factory?
                                             (first reference)
                                             reference))]

                (nil? callback-source)
                (let [kw-name (name callback-kw)]
                  (throw (ex-info (format "%s specified in schema not provided."
                                          error-name)
                                  {:reference reference
                                   :callbacks (keys callbacks-map)})))
                factory?
                (assoc field callback-kw (apply callback-source (rest reference)))

                :else
                (assoc field callback-kw callback-source)))
            field-container))

(defn attach-resolvers
  "Given a GraphQL schema and a map of keywords to resolver fns, replace
  each placeholder keyword in the schema with the actual resolver fn.

  resolver-m is a map from keyword to resolver function or resolver factory.

  When the value in the :resolve key is a keyword, it is simply replaced
  with the corresponding resolver function from resolver-m.

  Alternately, the :resolve value may be a seq, indicating a resolver factory.

  The first value in the seq is used to select the resolver factory function, which applied
  with the remaining values in the seq."
  [schema resolver-m]
  (let [f (fn [field-container]
            (attach-callbacks field-container resolver-m :resolve "Resolver"))
        f-object #(update % :fields f)]
    (-> schema
        (update? :objects #(map-vals f-object %))
        (update? :queries f)
        (update? :mutations f)
        (update? :subscriptions f))))

(defn attach-scalar-transformers
  "Given a GraphQL schema, attaches functions in the transform-m map to the schema.

  Inside each scalar definition, the :parse and :serialize keys are replaced with
  values from the transform-m map.

  In the initial schema, use a keyword for the :parse and :serialize keys, then
  provide a corresponding value in transform-m."
  [schema transform-m]
  (let [transform #(get transform-m % %)]
    (update schema :scalars
            #(map-vals (fn [scalar-def]
                         (-> scalar-def
                             (update :parse transform)
                             (update :serialize transform)))
                       %))))

(defn as-error-map
  "Converts an exception into an error map, including a :message key, plus
  any additional keys and values via `ex-data`."
  {:added "0.16.0"}
  [^Throwable t]
  (merge {:message (to-message t)}
         (ex-data t)))
