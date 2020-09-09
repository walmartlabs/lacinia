; Copyright (c) 2020-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.federation
  (:require
    [com.walmartlabs.lacinia.resolve :as resolve :refer [with-error]]
    [com.walmartlabs.lacinia.internal-utils :as utils]
    [com.walmartlabs.lacinia.schema :as schema]
    [clojure.spec.alpha :as s]))

(def foundation-types
  "Map of annotations and types to automatically include into an SDL
  schema used for federation."
  {:scalars
   {:_Any {:parse identity
           :serialize identity}
    :_FieldSet {:parse identity
                :serialize identity}}

   :objects
   {:_Service
    {:fields
     {:sdl {:type '(non-null String)}}}}

   :directive-defs
   {:external {:locations #{:field-definition}}
    :requires {:args {:fields {:type '(non-null :_FieldSet)}}
               :locations #{:field-definition}}
    :provides {:args {:fields {:type '(non-null :_FieldSet)}}
               :locations #{:field-definition}}
    :key {:args {:fields {:type '(non-null :_FieldSet)}}
          :locations #{:object :interface}}

    ;; We will need this as the schema model doesn't
    ;; track the concept of "extends" (it's handled by
    ;; the SDL schema parser).
    :extends {:locations #{:object :interface}}}})

(defn ^:private is-entity?
  [type-def]
  (some #(-> % :directive-type (= :key))
        (:directives type-def)))

(defn ^:private find-entity-names
  [schema]
  (->> schema
       :objects
       (reduce-kv (fn [coll type-name type-def]
                    (if (is-entity? type-def)
                      (conj coll type-name)
                      coll))
                  [])
       sort
       seq))

(defn ^:private prevent-collision
  [m ks]
  (when (some? (get-in m ks))
    (throw (IllegalStateException. (str "Key " (pr-str ks) " already exists in schema")))))

(defn ^:private entities-resolver-factory
  "Entity resolvers are special resolvers. They are passed
  the context, no args, and a seq of representations and return a seq
  of entities for those representations.

  entity-resolvers is a map of keyword to resolver fn."
  [entity-names entity-resolvers]
  (let [entity-names' (set entity-names)
        actual (-> entity-resolvers keys set)]
    (when (not= entity-names' actual)
      (throw (ex-info "Must provide entity resolvers for each entity (each type with @key)"
                      {:expected entity-names
                       :actual (sort actual)}))))
  (fn [context args _]
    (let [{:keys [representations]} args
          *errors (volatile! nil)
          grouped (group-by :__typename representations)
          results (reduce-kv
                    (fn [coll type-name reps]
                      (if-let [resolver (get entity-resolvers (keyword type-name))]
                        (let [result (resolver context {} reps)
                              result' (if (resolve/is-resolver-result? result)
                                        result
                                        (resolve/resolve-as result))]
                          (conj coll result'))
                        ;; Not found!  This is a sanity check as an implementing service
                        ;; should never be asked to resolve an entity it doesn't define (internal or external)
                        (do
                          (vswap! *errors conj {:message (str "No entity resolver for type " (utils/q type-name))})
                          coll)))
                    []
                    grouped)
          errors @*errors
          maybe-wrap (fn [result]
                       (if errors
                         (with-error result errors)
                         result))]
      ;; Quick optimization; don't do the aggregation if there's only a single
      ;; result (very common case).
      (case (count results)
        0 (maybe-wrap [])

        1 (if errors
            (utils/transform-result (first results) #(with-error % errors))
            (first results))

        (utils/aggregate-results results #(maybe-wrap (reduce into [] %)))))))

(defn inject-federation
  "Called after SDL parsing to extend the input schema
  (not the compiled schema) with federation support."
  [schema sdl entity-resolvers]
  (let [entity-names (find-entity-names schema)
        entities-resolver (entities-resolver-factory entity-names entity-resolvers)
        query-root (get-in schema [:roots :query] :QueryRoot)]
    (prevent-collision schema [:unions :_Entity])
    (prevent-collision schema [:objects query-root :fields :_service])
    (prevent-collision schema [:objects query-root :fields :_entities])
    (cond-> (assoc-in schema [:objects query-root :fields :_service]
                      {:type '(non-null :_Service)
                       :resolve (fn [_ _ _] {:sdl sdl})})
      entity-names (-> (assoc-in [:unions :_Entity :members] entity-names)
                       (assoc-in [:objects query-root :fields :_entities]
                                 {:type '(non-null (list :_Entity))
                                  :args
                                  {:representations
                                   {:type '(non-null (list (non-null :_Any)))}}
                                  :resolve entities-resolver})))))

(s/def ::entity-resolvers (s/map-of simple-keyword? ::schema/function-or-var))
