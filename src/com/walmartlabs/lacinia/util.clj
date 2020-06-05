; Copyright (c) 2017-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns com.walmartlabs.lacinia.util
  "Useful utility functions."
  (:require
    [com.walmartlabs.lacinia.internal-utils
     :as internal
     :refer [to-message map-vals cond-let update? apply-description
             name->path assoc-in!]]))

(defn ^:private attach-callbacks
  [field-container callbacks-map callback-kw error-name]
  (map-vals (fn [field]
              (cond-let
                :let [reference (get field callback-kw)]

                (nil? reference)
                field

                (fn? reference)
                field

                (var? reference)
                field

                :let [factory? (sequential? reference)
                      callback-source (get callbacks-map
                                           (if factory?
                                             (first reference)
                                             reference))]

                (nil? callback-source)
                (throw (ex-info (format "%s specified in schema not provided."
                                        error-name)
                                {:reference reference
                                 :callbacks (keys callbacks-map)}))
                factory?
                (assoc field callback-kw (apply callback-source (rest reference)))

                :else
                (assoc field callback-kw callback-source)))
            field-container))

(defn attach-resolvers
  "Given a GraphQL schema and a map of keywords to resolver fns, replace
  each placeholder keyword in the schema with the actual resolver fn.

  resolver-m is a map from of resolver functions and resolver function factories.
  The keys are usually keywords, but may be any value including string or symbol.

  When the value in the :resolve key is a simjple value, it is simply replaced
  with the corresponding resolver function from resolver-m.

  Alternately, the :resolve value may be a seq, indicating a resolver factory.

  The first value in the seq is used to select the resolver factory function, which
  is then invoked, via `apply`, with the remaining values in the seq."
  [schema resolver-m]
  (let [f (fn [field-container]
            (attach-callbacks field-container resolver-m :resolve "Resolver"))
        f-object #(update % :fields f)]
    (-> schema
        (update? :objects #(map-vals f-object %))
        (update? :queries f)
        (update? :mutations f)
        (update? :subscriptions f))))

(defn attach-streamers
  "Attaches stream handler functions to subscriptions.

  Replaces the :stream key inside subscription operations using the same logic as
  [[attach-resolvers]]."
  {:added "0.19.0"}
  [schema streamer-map]
  (update schema :subscriptions #(attach-callbacks % streamer-map :stream "Streamer")))

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

(defn ^{:added "0.36.0"} inject-enum-transformers
  "Given a GraphQL schema, injects transformers for enums into the schema.

  transform-m maps from the scalar name (a keyword) to a map with keys :parse
  and/or :serialize; these are applied to the Enum.

  Each enum must exist, or an exception is thrown."
  [schema transform-m]
  (let [f (fn [enums enum m]
            (when-not (contains? enums enum)
              (throw (ex-info "Undefined enum when injecting enum transformer."
                              {:enum enum
                               :enums (-> enums keys sort vec)})))
            (let [{:keys [parse serialize]} m]
              (update enums enum #(cond-> %
                                          parse (assoc :parse parse)
                                          serialize (assoc :serialize serialize)))))]
    (update schema :enums #(reduce-kv f % transform-m))))

(defn inject-scalar-transformers
  "Given a GraphQL schema, injects transformers for scalars into the schema.

  transform-m maps from the scalar name (a keyword) to a map with keys :parse
  and :serialize; these are applied to the Enum.

  Each scalar must exist, or an exception is thrown."
  {:added "0.37.0"}
  [schema transform-m]
  (let [f (fn [scalars scalar m]
            (when-not (contains? scalars scalar)
              (throw (ex-info "Undefined scalar when injecting scalar transformer"
                              {:scalar scalar
                               :scalars (-> scalars keys sort vec)})))
            (let [{:keys [parse serialize]} m]
              (update scalars scalar assoc :parse parse :serialize serialize)))]
    (update schema :scalars #(reduce-kv f % transform-m))))

(defn as-error-map
  "Converts an exception into an error map, including a :message key, plus
  any additional keys and values via `ex-data`.

  In the second arity, a further map of values to be merged into the error
  map can be provided."
  {:added "0.16.0"}
  ([^Throwable t]
   (as-error-map t nil))
  ([^Throwable t more-data]
   (let [extension-data (merge (ex-data t) more-data)
         locations (:locations extension-data)
         remaining-data (dissoc extension-data :locations)]
     (cond-> {:message (to-message t)}
             locations (assoc :locations locations)
             (seq remaining-data) (assoc :extensions remaining-data)))))

(defn inject-descriptions
  "Injects documentation into a schema, as `:description` keys on various elements
  within the schema.

  The documentation map keys are keywords with a particular structure,
  and the values are formatted Markdown strings.

  The keys are one of the following forms:

  - `:Type`
  - `:Type/name`
  - `:Type/name.argument`

  A simple `Type` will document an object, input object, interface, union, or enum.

  The second form is used to document a field of an object, input object, or interface, or
  to document a specific value of an enum (e.g., `:Episode/NEW_HOPE`).

  The final form is used to document an argument to a field (it does not make sense for enums).

  Additionally, the `Type` can be `queries`, `mutations`, or `subscriptions`, in which case
  the `name` will be the name of the operation (e.g., `:queries/episode`).

  An exception is thrown if an element identified by a key does not exist.

  See [[parse-docs]]."
  {:added "0.27.0"}
  [schema documentation]
  (reduce-kv (fn [schema' location description]
               (apply-description schema' location description))
             schema
             documentation))

(defn inject-resolvers
  "Adds resolvers to the schema.  The resolvers map is a map of keywords to
  field resolvers (as functions, or [[FieldResolver]] instances).

  The key identifies where the resolver should be added, in the form `:Type/field`.

  Alternately, the key may be of the format `:queries/name` (or `:mutations/name` or
  `:subscriptions/name`).

  Throws an exception if the target of the resolver can't be found.

  In many cases, this is a full replacement for [[attach-resolvers]], but the two functions
  can also be used in conjunction with each other."
  {:added "0.27.0"}
  [schema resolvers]
  (reduce-kv (fn [schema' k resolver]
               (assoc-in! schema' (name->path schema' k :resolve) resolver))
             schema
             resolvers))

(defn inject-streamers
  "As [[inject-resolvers]] but the updated key is :stream, thereby supplying a subscription
  streamer function."
  {:added "0.37.0"}
  [schema streamers]
  (reduce-kv (fn [schema' k streamer]
               (assoc-in! schema' (name->path schema' k :stream) streamer))
             schema
             streamers))
