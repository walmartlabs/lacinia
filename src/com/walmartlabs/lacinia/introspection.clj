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

(ns ^:no-doc com.walmartlabs.lacinia.introspection
  "Uses the compiled schema to expose introspection."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.internal-utils :refer [remove-keys is-internal-type-name? cond-let]]
    [com.walmartlabs.lacinia.constants :as constants]
    [clojure.string :as str]
    [clojure.data.json :as json]
    [clojure.spec.alpha :as s]))

(def ^:private category->kind
  {:scalar :SCALAR
   :object :OBJECT
   :interface :INTERFACE
   :union :UNION
   :enum :ENUM
   :input-object :INPUT_OBJECT})

(defn ^:private schema-type
  ([schema type-def]
   (schema-type schema type-def (:type-name type-def)))
  ([schema type-def type-name]
   (let [{:keys [category description]} type-def]
     {:kind (category->kind category)
      :name (when type-name
              (name type-name))
      :description description
      ;; Needed by sub-resolvers for certain types
      ::category category
      ::type-def type-def})))

(defn ^:private type-name->schema-type
  [schema type-name]
  (schema-type schema (get schema type-name)))

(defn ^:private resolve-interfaces
  [context _ value]
  (when-let [interfaces (-> value ::type-def :implements sort seq)]
    (let [schema (get context constants/schema-key)]
      (map #(type-name->schema-type schema %)
           interfaces))))

(defn ^:private is-deprecated?
  "The :deprecated key can either be a boolean, or a string which is the deprecation reason."
  [deprecated]
  (cond
    (true? deprecated)
    true

    (string? deprecated)
    (not (str/blank? deprecated))

    :else
    false))

(defn ^:private resolve-field
  [field-def]
  (let [{:keys [deprecated description args]} field-def]
    {:name (-> field-def :field-name name)
     :description description
     :args (for [arg-def (->> args vals (sort-by :arg-name))]
             {:name (-> arg-def :arg-name name)
              :description (:description arg-def)
              ::default-value (:default-value arg-def)
              ;; This is for resolve-nested-type, but it might actually be
              ;; a field definition, argument definition, or input value (a field of
              ;; an input object).
              ::type-map (:type arg-def)})
     :isDeprecated (is-deprecated? deprecated)
     :deprecationReason (when (string? deprecated) deprecated)
     ::type-map (:type field-def)}))

(defn ^:private resolve-fields
  [_ args object-or-interface]
  (let [{:keys [::category ::type-def]} object-or-interface
        {:keys [includeDeprecated]} args]
    (when (#{:object :interface} category)
      (map #(resolve-field %)
           (->> type-def
                :fields
                vals
                (remove #(-> % :field-name is-internal-type-name?))
                (filter #(or includeDeprecated
                             (-> % :deprecated is-deprecated? not)))
                (sort-by :field-name))))))

(defn ^:private resolve-root-schema
  [context _ _]
  (let [schema (get context constants/schema-key)
        type-names (remove is-internal-type-name? (keys schema))
        root (:com.walmartlabs.lacinia.schema/roots schema)
        queries-root (-> (get schema (:query root))
                         (update :fields #(remove-keys is-internal-type-name? %)))
        mutations-root (get schema (:mutation root))
        omit-mutations (-> mutations-root :fields empty?)
        subs-root (get schema (:subscription root))
        omit-subs (-> subs-root :fields empty?)
        type-names' (cond-> (set type-names)
                      omit-mutations (disj (:mutation root))
                      omit-subs (disj (:subscription root)))
        not-null-boolean {:kind :non-null
                          :type {:kind :root
                                 :type :Boolean}}]
    (cond-> {:directives [{:name "skip"
                           :description "Skip the selection only when the `if` argument is true."
                           :locations [:INLINE_FRAGMENT :FIELD :FRAGMENT_SPREAD]
                           :args [{:name "if"
                                   :description "Triggering argument for skip directive."
                                   ::type-map not-null-boolean}]}
                          {:name "include"
                           :description "Include the selection only when the `if` argument is true."
                           :locations [:INLINE_FRAGMENT :FIELD :FRAGMENT_SPREAD]
                           :args [{:name "if"
                                   :description "Triggering argument for include directive."
                                   ::type-map not-null-boolean}]}]
             :types (->> type-names'
                         sort
                         (map #(type-name->schema-type schema %)))
             :queryType (schema-type schema queries-root)}

      (not omit-mutations)
      (assoc :mutationType (schema-type schema mutations-root))

      (not omit-subs)
      (assoc :subscriptionType (schema-type schema subs-root)))))


(defn ^:private resolve-root-type
  [context args _]
  (let [schema (get context constants/schema-key)
        type-name (-> args :name keyword)]
    (type-name->schema-type schema type-name)))

(defn ^:private resolve-enum-values
  [_ args value]
  (let [{:keys [::category ::type-def]} value
        {:keys [includeDeprecated]} args]
    (when (= :enum category)
      ;; Use the ordered list, not the set, in case order
      ;; has meaning (unlike elsewhere we we sort alphabetically).
      (for [value (get type-def :values)
            :let [{:keys [description deprecated]} (get-in type-def [:values-detail value])
                  is-deprecated (is-deprecated? deprecated)]
            :when (or includeDeprecated
                      (not is-deprecated))]
        {:name (name value)
         :description description
         :isDeprecated is-deprecated
         :deprecationReason (when (string? deprecated) deprecated)}))))

(defn ^:private resolve-input-fields
  [context _ value]
  (let [type-def (::type-def value)]
    ;; __InputValue is very close to __Field
    (when (= :input-object (:category type-def))
      (for [field-def (->> type-def :fields vals (sort-by :field-name))]
        {:name (-> field-def :field-name name)
         :description (:description field-def)
         ::default-value (:default-value field-def)
         ::type-map (:type field-def)}))))

(defn ^:private resolve-possible-types
  [context _ value]
  (let [schema (get context constants/schema-key)
        {:keys [::category ::type-def]} value]
    (when (#{:interface :union} category)
      (map #(type-name->schema-type schema %)
           (->> type-def :members sort)))))

(defn ^:private resolve-nested-type
  [context _ value]
  (let [schema (get context constants/schema-key)
        {:keys [kind type]} (::type-map value)]
    (case kind

      :list
      {:kind :LIST
       ::type-map type}

      :non-null
      {:kind :NON_NULL
       ::type-map type}

      :root
      (type-name->schema-type schema type)

      nil)))

(defn ^:private resolve-of-type
  [context _ value]
  (when (::type-map value)
    (resolve-nested-type context nil value)))

(defmulti emit-default-value
  (fn [schema type-map value]
    (cond-let
      (nil? value)
      ::null

      :let [kind (:kind type-map)]

      (= :root kind)
      (get-in schema [(:type type-map) :category])

      :else
      kind)))

(defmethod emit-default-value ::null
  [_ _ _]
  nil)

(defmethod emit-default-value :non-null
  [schema type-map value]
  (emit-default-value schema (:type type-map) value))

(defmethod emit-default-value :scalar
  [schema type-map value]
  (let [type-name (:type type-map)
        scalar-def (get schema type-name)
        serialized (-> scalar-def :serialize (s/conform value))]
    (if (string? serialized)
      (json/write-str serialized)
      (str serialized))))

(defmethod emit-default-value :enum
  [_ _ value]
  (name value))

(defmethod emit-default-value :list
  [schema type-map value]
  (let [nested (:type type-map)]
    (str "["
         (->> value
              (map #(emit-default-value schema nested %))
              (str/join ","))
         "]")))

(defmethod emit-default-value :input-object
  [schema type-map value]
  (let [type-def (get schema (:type type-map))
        kvs (keep (fn [field-def]
                    (let [field-name (:field-name field-def)
                          field-value (emit-default-value schema
                                                          (:type field-def)
                                                          (get value field-name))]
                      (when field-value
                        (str \"
                             (name field-name)
                             "\":"
                             field-value))))
                  (->> type-def :fields vals (sort-by :field-name)))]
    (str "{"
         (str/join "," kvs)
         "}")))


(defn ^:private default-value
  [context _ input-value]
  (emit-default-value (get context constants/schema-key)
                      (::type-map input-value)
                      (::default-value input-value)))

(defn introspection-schema
  "Builds an returns the introspection schema, which can be merged into the user schema."
  []
  (-> "com/walmartlabs/lacinia/introspection.edn"
      io/resource
      slurp
      edn/read-string
      (util/attach-resolvers {:root-type resolve-root-type
                              :root-schema resolve-root-schema
                              :fields resolve-fields
                              :enum-values resolve-enum-values
                              :input-fields resolve-input-fields
                              :nested-type resolve-nested-type
                              :interfaces resolve-interfaces
                              :of-type resolve-of-type
                              :possible-types resolve-possible-types
                              :default-value default-value})))
