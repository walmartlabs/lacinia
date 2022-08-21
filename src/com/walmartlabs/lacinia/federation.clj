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
   [com.walmartlabs.lacinia.internal-utils :as utils :refer [get-nested]]
   [com.walmartlabs.lacinia.resolve-utils :as ru]
   [com.walmartlabs.lacinia.schema :as schema]
   [clojure.spec.alpha :as s]
   [clojure.string :refer [join]]))

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

  entity-resolvers is a map of keyword to resolver fn (or FieldResolver instance)s."
  [entity-names entity-resolvers]
  (let [entity-names' (set entity-names)
        actual (-> entity-resolvers keys set)
        entity-resolvers' (utils/map-vals resolve/as-resolver-fn entity-resolvers)]
    (when (not= entity-names' actual)
      (throw (ex-info "Must provide entity resolvers for each entity (each type with @key)"
                      {:expected entity-names
                       :actual (sort actual)})))
    (fn [context args _]
      (let [{:keys [representations]} args
            *errors (volatile! nil)
            grouped (group-by :__typename representations)
            results (reduce-kv
                      (fn [coll type-name reps]
                        (if-let [resolver (get entity-resolvers' (keyword type-name))]
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
                           (reduce with-error result errors)
                           result))]
        ;; Quick optimization; don't do the aggregation if there's only a single
        ;; result (very common case).
        (case (count results)
          0 (maybe-wrap [])

          1 (if errors
              (ru/transform-result (first results) #(reduce with-error % errors))
              (first results))

          (ru/aggregate-results results #(maybe-wrap (reduce into [] %))))))))

(defn ^:private apply-list
  [f x]
  (if (-> x first seq?)
    (apply f x)
    (f x)))

(defn ^:private indent
  [s]
  (cond
    (clojure.string/blank? s) ""
    :else (str "  " (clojure.string/replace s #"\n" "\n  "))))

(defn ^:private edn-description->sdl-description
  [description]
  (if (nil? description)
    ""
    (str "\"\"\"\n" description "\n\"\"\"\n")))

(defn ^:private edn-type->sdl-type
  [type]
  (if (seq? type)
    (let [[hd & tl] type]
      (cond
        (nil? hd) ""
        (= 'non-null hd) (str (apply-list edn-type->sdl-type tl) "!")
        (= 'list hd) (str "[" (apply-list edn-type->sdl-type tl) "]")
        (= 'String hd) "String"
        (= 'Int hd) "Int"
        (= 'Float hd) "Float"
        (= 'Boolean hd) "Boolean"
        (= 'ID hd) "ID"
        (keyword? hd) (name hd)
        (symbol? hd) (name hd)))
    (recur (list type))))

(defn ^:private value->string
  [value]
  (cond
    (string? value) (str "\"" value "\"")
    (keyword? value) (name value)
    :else (str value)))

(defn ^:private edn-default-value->sdl-default-value
  [default-value]
  (if (nil? default-value)
    ""
    (str " = " (value->string default-value))))

(defn ^:private edn-arg-descrption->sdl-arg-description
  [description]
  (if (nil? description)
    ""
    (str "\"" description "\" ")))

(defn ^:private edn-args->sdl-args
  [args]
  (if (nil? args)
    ""
    (str "(" (join ", " (map (fn [[arg-name {:keys [type default-value description]}]] (str (edn-arg-descrption->sdl-arg-description description) (name arg-name) ": " (edn-type->sdl-type type) (edn-default-value->sdl-default-value default-value))) args)) ")")))

(defn ^:private edn-directive-args->sdl-directive-args
  [directive-args]
  (if (nil? directive-args)
    ""
    (str "(" (->> directive-args
                  (map (fn [[arg-name arg-value]] (str (name arg-name) ": " (value->string arg-value))))
                  (join ", ")) ")")))

(defn ^:private edn-directives->sdl-directives
  [directives]
  (if (nil? directives)
    ""
    (str " "
         (->> directives
              (map (fn [{:keys [directive-type directive-args]}]
                     (str "@" (name directive-type) (edn-directive-args->sdl-directive-args directive-args))))
              (join " ")) " ")))

(defn ^:private edn-fields->sdl-fields
  [fields]
  (str
   "{\n"
   (->> fields
        (map (fn [[field-name {:keys [type args description]}]]
               (str (edn-description->sdl-description description) (name field-name) (edn-args->sdl-args args) ": " (edn-type->sdl-type type))))
        (join "\n")
        indent)
   "\n}"))

(defn ^:private edn-implements->sdl-implements
  [implements]
  (if (seq implements)
    (str " implements " (->> implements
                             (map name)
                             (join " & ")))
    ""))

(defn ^:private edn-objects->sdl-objects
  [objects]
  (->> objects
       (map (fn [[key {:keys [fields directives description implements]}]]
              (str (edn-description->sdl-description description)
                   "type "
                   (name key)
                   (edn-implements->sdl-implements implements)
                   (edn-directives->sdl-directives directives)
                   (edn-fields->sdl-fields fields))))
       (join "\n")))

(defn ^:private edn-interfaces->sdl-interfaces
  [interfaces]
  (->> interfaces
       (map (fn [[key val]]
              (str "interface "
                   (name key)
                   (-> val :fields edn-fields->sdl-fields))))
       (join "\n")))

(defn ^:private edn-input-objects->sdl-input-objects
  [input-objects]
  (->> input-objects
       (map (fn [[key val]]
              (str "input "
                   (name key)
                   (-> val :fields edn-fields->sdl-fields))))
       (join "\n")))

(defn ^:private edn-unions->sdl-unions
  [unions]
  (->> unions
       (map (fn [[union-name {members :members}]]
              (str "union " (name union-name) " = " (->> members
                                                         (map name)
                                                         (join " | ")))))
       (join "\n")))

(defn ^:private edn-enum-value->sdl-enum-value
  [enum-value]
  (cond
    (keyword? enum-value) enum-value
    :else (:enum-value enum-value)))

(defn ^:private edn-enums->sdl-enums
  [enums]
  (->> enums
       (map (fn [[enum-name {values :values}]]
              (str "enum " (name enum-name) "{\n" (->> values (map edn-enum-value->sdl-enum-value) (map name) (join "\n") indent) "\n}")))
       (join "\n")))

(defn ^:private edn-scalars->sdl-scalars
  [scalars]
  (->> (keys scalars)
       (map name)
       sort
       (map #(str "scalar " %))
       (join "\n")))

(def directive-targets
  {:enum                   "ENUM"
   :input-field-definition "INPUT_FIELD_DEFINITION"
   :interface              "INTERFACE"
   :input-object           "INPUT_OBJECT"
   :enum-value             "ENUM_VALUE"
   :scalar                 "SCALAR"
   :argument-definition    "ARGUMENT_DEFINITION"
   :union                  "UNION"
   :field-definition       "FIELD_DEFINITION"
   :object                 "OBJECT"
   :schema                 "SCHEMA"})

(defn ^:private edn-directive-defs->sdl-directives
  [directive-defs]
  (->> directive-defs
       (map (fn [[directive-name {:keys [locations args]}]]
              (str "directive @"
                   (name directive-name)
                   (edn-args->sdl-args args)
                   " on "
                   (->> locations
                        (map directive-targets)
                        (join " | ")))))
       (join "\n")))

(defn ^:private edn-roots->sdl-schema
  [{:keys [query mutation subscription]}]
  (cond-> "schema {"
    (some? query) (str "\n  query: " (name query))
    (some? mutation) (str "\n  mutation: " (name mutation))
    (some? subscription) (str "\n  subscription: " (name subscription))
    true (str "\n}"))
  )

(defn ^:private fold-queries
  [{:keys [queries] :as schema}]
  (cond
    (map? queries) (update-in schema [:objects :Query :fields] merge queries)
    :else schema))

(defn ^:private fold-mutations
  [{:keys [mutations] :as schema}]
  (cond
    (map? mutations) (update-in schema [:objects :Mutation :fields] merge mutations)
    :else schema))

(defn ^:private fold-subscriptions
  [{:keys [subscriptions] :as schema}]
  (cond
    (map? subscriptions) (update-in schema [:objects :Subscription :fields] merge subscriptions)
    :else schema))

(defn generate-sdl
  "Translate the edn lacinia schema to the SDL schema."
  [schema]
  (->> schema
       fold-queries
       fold-mutations
       fold-subscriptions
       (sort-by #(-> % first {:directive-defs 1
                              :scalars 2
                              :enums 3
                              :unions 4
                              :interfaces 5
                              :input-objects 6
                              :objects 7}))
       (map (fn [[key val]]
              (case key
                :objects (edn-objects->sdl-objects val)
                :interfaces (edn-interfaces->sdl-interfaces val)
                :scalars (edn-scalars->sdl-scalars val)
                :unions (edn-unions->sdl-unions val)
                :input-objects (edn-input-objects->sdl-input-objects val)
                :enums (edn-enums->sdl-enums val)
                :directive-defs (edn-directive-defs->sdl-directives val)
                :roots (edn-roots->sdl-schema val)
                "")))
       (join "\n\n")))

(defn inject-federation
  "Called after SDL parsing to extend the input schema
  (not the compiled schema) with federation support.
   If the SDL string is not given, it is automatically created through the schema."
  ([schema entity-resolvers]
   (inject-federation schema (generate-sdl schema) entity-resolvers))
  ([schema sdl entity-resolvers]
   (let [entity-names (find-entity-names schema)
         entities-resolver (entities-resolver-factory entity-names entity-resolvers)
         query-root (get-nested schema [:roots :query] :Query)]
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
                                   :resolve entities-resolver}))))))

(s/def ::entity-resolvers (s/map-of simple-keyword? ::schema/resolve))
