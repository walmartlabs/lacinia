(ns com.walmartlabs.lacinia.parser.schema
  (:require [clojure.walk :refer [prewalk]]
            [clj-antlr.core :as antlr.core]
            [clj-antlr.proto :as antlr.proto]
            [clojure.java.io :as io]
            [clj-antlr.common :as antlr.common]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.parser :refer [antlr-parse parse-failures]])
  (:import (clj_antlr ParseError)
           (clojure.lang ExceptionInfo)))

(def ^:private grammar
  (antlr.core/parser (slurp (io/resource "com/walmartlabs/lacinia/schema.g4"))))

(defn ^:private select
  "Selects nodes from the ANTLR parse tree given a path, which is a
  vector of alternatively node keyword labels, sets of node keyword
  labels or predicate functions that accept a node as a
  parameter. Always accepts and returns a sequence of nodes."
  [path nodes]
  (let [[p & rst] path]
    (cond->> (seq (mapcat
                   (fn [node]
                     (map rest
                          (filter (cond
                                    (keyword? p) #(= (first %) p)
                                    (set? p) #(p (first %))
                                    (fn? p) p)
                                  node)))
                   nodes))
      (seq rst) (recur rst))))

(defn ^:private select-map
  "Maps over selected nodes, providing a sequence of nodes as a
  shortcut to facilitate using selectors within f."
  [f path nodes]
  (map (comp f vector) (select path nodes)))

(defn ^:private select1
  "Selects a terminal scalar value. Should only be used when path
  resolves to a single scalar."
  [path nodes]
  (ffirst (select path nodes)))

(defn ^:private xform-type-name
  [typename]
  (if (#{"Boolean" "String" "Int" "Float" "ID"} typename)
    (symbol typename)
    (keyword typename)))

(defn ^:private xform-typespec
  [typespec]
  (if-let [list-type-name (select1 [:listType :typeName :name] typespec)]
    (list 'list (xform-type-name list-type-name))
    (xform-type-name (select1 [:typeName :name] typespec))))

(declare ^:private xform-map-value)

(defn ^:private xform-default-value
  [arg-value]
  (let [[type value & _] arg-value]
    (case type
      :nullvalue nil
      :enumValue (keyword (second value))
      :arrayValue (mapv (comp xform-default-value second) (rest arg-value))
      :objectValue (apply merge (select-map xform-map-value [:objectField] [(rest arg-value)]))
      value)))

(defn ^:private xform-map-value
  [object-field]
  {(keyword (select1 [:name] object-field))
   (some-> (select1 [:value] object-field)
           (xform-default-value))})

(defn ^:private xform-field-arg
  [arg]
  {(keyword (select1 [:name] arg))
   {:type (xform-typespec (select [:typeSpec] arg))
    :defaultValue (some-> (select1 [:defaultValue :value] arg)
                          (xform-default-value))}})

(defn ^:private xform-field
  [field]
  {(keyword (select1 [:fieldName :name] field))
   (cond-> {:type (xform-typespec (select [:typeSpec] field))}
     (select [:fieldArgs] field) (assoc :args (apply merge (select-map xform-field-arg [:fieldArgs :argument] field))))})

(defn ^:private xform-type
  [type]
  {(keyword (select1 [:typeName :name] type))
   (cond-> {:fields (apply merge (select-map xform-field [:fieldDef] type))}
     (select1 [:implementationDef] type) (-> (assoc :implements
                                                    (mapv keyword (select [:implementationDef :typeName :name] type)))))})

(defn ^:private xform-enum
  [enum]
  {(keyword (select1 [:typeName :name] enum))
   {:values (vec (map first (select [:scalarName :name] enum)))}})

(defn ^:private xform-operation
  [schema operation]
  (let [operation-type (keyword (select1 [:typeName :name] operation))]
    (or (:fields (get-in schema [:objects operation-type]))
        ;; Since Lacinia schemas do not support specifying a
        ;; union type as an operation directly but the
        ;; GraphQL schema language does, then we need to
        ;; resolve the union here.
        (some->> (get-in schema [:unions operation-type :members])
                 (map #(get-in schema [:objects % :fields]))
                 (apply merge))
        (throw (ex-info "Operation type not found" {:operation operation-type})))))

(defn ^:private xform-scalar
  [scalar]
  {(keyword (select1 [:typeName :name] scalar))
   {:parse identity
    :serialize identity}})

(defn ^:private xform-union
  [union]
  {(keyword (select1 [:typeName :name] union))
   {:members (mapv (comp keyword first)
                   (select [:unionTypes :typeName :name] union))}})

(defn ^:private attach-operations
  "Since Lacinia schemas do not support providing simple type names as
  queries or mutations, but this is how the GraphQL schema language
  operates, this resolves the queries/mutations from types.

  Note that one downside of this is that there will be extra, unused
  object types floating around in the Lacinia schema."
  [schema root]
  (assoc schema
         :queries (apply merge
                         (select-map #(xform-operation schema %)
                                     [:schemaDef :operationTypeDef :queryOperationDef]
                                     root))
         :mutations (apply merge
                           (select-map #(xform-operation schema %)
                                       [:schemaDef :operationTypeDef :mutationOperationDef]
                                       root))))

(defn ^:private xform-schema
  "Given an ANTLR parse tree, returns a Lacinia schema."
  [antlr-tree]
  (let [root (select [:graphqlSchema] [[antlr-tree]])]
    (-> {:objects (apply merge (select-map xform-type [#{:inputTypeDef :typeDef}] root))
         :enums (apply merge (select-map xform-enum [:enumDef] root))
         :scalars (apply merge (select-map xform-scalar [:scalarDef] root))
         :unions (apply merge (select-map xform-union [:unionDef] root)) ;; TODO -- also ensure unions can be used for query/mutations
         :interfaces (apply merge (select-map xform-type [:interfaceDef] root))}
        (attach-operations root))))

(defn parse-schema
  "Given a GraphQL schema string, parses it and returns a Lacinia EDN
  schema. Defers validation of the schema to the downstream schema
  validator."
  [schema-string]
  (xform-schema (try
                  (antlr-parse grammar schema-string)
                  (catch ParseError e
                    (let [failures (parse-failures e)]
                      (throw (ex-info "Failed to parse GraphQL schema."
                                      {:errors failures})))))))
