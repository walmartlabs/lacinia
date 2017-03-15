(ns ^:no-doc com.walmartlabs.lacinia.introspection
  "Uses the compiled schema to expose introspection."
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.internal-utils :refer [remove-keys is-internal-type-name?]]
    [clojure.string :as str]
    [com.walmartlabs.lacinia.constants :as constants]))

(def ^:private category->kind
  {:scalar "SCALAR"
   :object "OBJECT"
   :interface "INTERFACE"
   :union "UNION"
   :enum "ENUM"
   :input-object "INPUT_OBJECT"})

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

(defn ^:private resolve-field
  [schema field-def]
  {:name (-> field-def :field-name name)
   :description (:description field-def)
   :args (for [arg-def (->> field-def :args vals (sort-by :arg-name))]
           {:name (-> arg-def :arg-name name)
            :description (:description arg-def)
            :defaultValue (:default-value arg-def)
            ;; This is for resolve-nested-type, but it might actually be
            ;; a field definition, argument definition, or input value (a field of
            ;; an input object).
            ::field-def arg-def})
   :isDeprecated false
   ::field-def field-def})

(defn ^:private resolve-fields
  [context args object-or-interface]
  (let [schema (get context constants/schema-key)
        {:keys [::category ::type-def]} object-or-interface]
    (when (#{:object :interface} category)
      (map #(resolve-field schema %)
           (->> type-def
                :fields
                vals
                (remove #(-> % :field-name is-internal-type-name?))
                (sort-by :field-name))))))

(defn ^:private resolve-root-schema
  [context _ _]
  (let [schema (get context constants/schema-key)
        type-names (remove is-internal-type-name? (keys schema))
        queries-root (-> (get schema constants/query-root)
                         (update :fields #(remove-keys is-internal-type-name? %)))
        mutations-root (get schema constants/mutation-root)
        omit-mutations (-> mutations-root :fields empty?)
        type-names' (if omit-mutations
                      (-> type-names set (disj constants/mutation-root))
                      type-names)]
    (cond-> {:directives []                                 ; TODO!
             :types (->> type-names'
                         sort
                         (map #(type-name->schema-type schema %)))
             :queryType (schema-type schema queries-root)}

      (not omit-mutations)
      (assoc :mutationType (schema-type schema mutations-root)))))


(defn ^:private resolve-root-type
  [context args _]
  (let [schema (get context constants/schema-key)
        type-name (-> args :name keyword)]
    (type-name->schema-type schema type-name)))

(defn ^:private resolve-enum-values
  [context _ value]
  (let [schema (get context constants/schema-key)
        {:keys [::category ::type-def]} value]
    (when (= :enum category)
      ;; Use the ordered list, not the set, in case order
      ;; has meaning (unlike elsewhere we we sort alphabetically).
      (for [value (get type-def :values)]
        {:name value
         :isDeprecated false}))))

(defn ^:private resolve-input-fields
  [context _ value]
  (let [schema (get context constants/schema-key)
        type-def (::type-def value)]
    ;; __InputValue is very close to __Field
    (when (= :input-object (:category type-def))
      (for [field-def (->> type-def :fields vals (sort-by :field-name))]
        {:name (-> field-def :field-name name)
         :description (:description field-def)
         :defaultValue (:default-value field-def)
         ::field-def field-def}))))

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
        field-def (::field-def value)
        {:keys [type multiple? non-nullable?]} field-def]
    (cond
      multiple?
      {:kind "LIST"
       ::field-def (assoc field-def :multiple? false)}

      non-nullable?
      {:kind "NON_NULL"
       ::field-def (assoc field-def :non-nullable? false)}

      :else
      (type-name->schema-type schema type))))

(defn ^:private resolve-of-type
  [context _ value]
  (when (::field-def value)
    (resolve-nested-type context nil value)))

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
                              :possible-types resolve-possible-types})))
