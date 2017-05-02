(ns com.walmartlabs.lacinia.util
  "Useful utility functions."
  (:require
    clojure.walk
    [com.walmartlabs.lacinia.internal-utils :refer [to-message map-vals]]))

(defn attach-resolvers
  "Given a GraphQL schema and a map of keywords to resolver fns, replace
  each placeholder keyword in the schema with the actual resolver fn."
  [schema resolver-m]
  (clojure.walk/postwalk
   (fn [node]
     (if (and (sequential? node) (= :resolve (first node)))
       (let [resolver-k (second node)
             resolver (if (keyword? resolver-k)
                        (get resolver-m resolver-k)
                        (get resolver-m (first resolver-k)))]
         (cond
           (nil? resolver)
           (throw (ex-info "Resolver specified in schema not provided."
                           {:requested-resolver resolver-k
                            :provided-resolvers (keys resolver-m)}))

           (keyword? resolver-k)
           [:resolve resolver]

           :else
           ;; If resolver-k is not a keyword, it must be a sequence,
           ;; in which first element is a key that points to a resolver
           ;; factory in resolver-m and subsequent elements are arguments
           ;; for the given factory.
           [:resolve (apply resolver (rest resolver-k))]))
       node))
   schema))


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
