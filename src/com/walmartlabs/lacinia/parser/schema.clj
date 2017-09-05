(ns com.walmartlabs.lacinia.parser.schema
  (:require [com.walmartlabs.lacinia.internal-utils :refer [remove-vals]]
            [com.walmartlabs.lacinia.parser :refer [antlr-parse parse-failures stringvalue->String]]
            [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clj-antlr.core :as antlr.core])
  (:import (clj_antlr ParseError)
           (clojure.lang ExceptionInfo)))

;; When using Clojure 1.9 alpha, the dependency on clojure-future-spec can be excluded,
;; and this code will not trigger
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [simple-keyword?]]))

(def ^:private grammar
  (antlr.core/parser (slurp (io/resource "com/walmartlabs/lacinia/schema.g4"))))

(defn ^:private rest-or-true
  "Return (rest coll), or true if coll only contains a single element."
  [coll]
  (or (seq (rest coll))
      [true]))

(defn ^:private select
  "Selects nodes from the ANTLR parse tree given a path, which is a
  vector of alternatively node keyword labels, sets of node keyword
  labels or predicate functions that accept a node as a
  parameter. Always accepts and returns a sequence of nodes."
  [path nodes]
  (let [[p & rst] path]
    (cond->> (seq (mapcat
                   (fn [node]
                     (map rest-or-true
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
  (cond
    ;; list
    (select1 [:listType] typespec) (cond->> (list 'list (xform-typespec (select [:listType :typeSpec] typespec)))
                                     (select1 [:required] typespec) (list 'non-null))
    ;; scalar
    :else (cond->> (xform-type-name (select1 [:typeName :name] typespec))
            (select1 [:required] typespec) (list 'non-null))))

(declare ^:private xform-map-value)

(defn ^:private xform-default-value
  [arg-value]
  (let [[type value & _] arg-value]
    (case type
      :nullvalue nil
      :enumValue (keyword (second value))
      :arrayValue (mapv (comp xform-default-value second) (rest arg-value))
      :objectValue (apply merge (select-map xform-map-value [:objectField] [(rest arg-value)]))
      :stringvalue (stringvalue->String value)
      value)))

(defn ^:private xform-map-value
  [object-field]
  {(keyword (select1 [:name] object-field))
   (some-> (select1 [:value] object-field)
           (xform-default-value))})

(defn ^:private xform-field-arg
  [arg]
  {(keyword (select1 [:name] arg))
   (let [field-arg {:type (xform-typespec (select [:typeSpec] arg))}]
     (if-let [default-value (some-> (select1 [:defaultValue :value] arg)
                                    (xform-default-value))]
       (assoc field-arg :defaultValue default-value)
       field-arg))})

(defn ^:private xform-field
  [field]
  {(keyword (select1 [:fieldName :name] field))
   (cond-> {:type (xform-typespec (select [:typeSpec] field))}
     (select [:fieldArgs] field) (assoc :args (apply merge (select-map xform-field-arg [:fieldArgs :argument] field))))})

(defn ^:private xform-type
  [type]
  {(keyword (select1 [:typeName :name] type))
   (cond-> {:fields (apply merge (select-map xform-field [:fieldDef] type))}
     (select1 [:implementationDef] type) (assoc :implements
                                                (mapv (comp keyword first)
                                                      (select [:implementationDef :typeName :name] type))))})

(defn ^:private xform-enum
  [enum]
  {(keyword (select1 [:typeName :name] enum))
   {:values (vec (map (comp keyword first) (select [:scalarName :name] enum)))}})

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

(defn ^:private attach-resolvers
  [schema resolvers]
  (reduce-kv (fn [schema' type fields]
               (reduce-kv (fn [schema'' field resolver]
                            (assoc-in schema'' [:objects type :fields field :resolve] resolver))
                          schema'
                          fields))
             schema
             resolvers))

(defn ^:private attach-scalars
  [schema scalars]
  ;; This wipes out the placeholder scalar values since we don't want
  ;; to fallback to any sort of unexpected default behavior.
  (assoc schema :scalars scalars))

(defn ^:private attach-documentation
  [schema documentation]
  (reduce-kv (fn [schema' type {:keys [fields description]}]
               (cond-> (reduce-kv (fn [schema'' field field-descr]
                                    (assoc-in schema'' [:objects type :fields field :description] field-descr))
                                  schema'
                                  fields)
                 description (assoc-in [:objects type :description] description)))
             schema
             documentation))

(defn ^:private xform-schema
  "Given an ANTLR parse tree, returns a Lacinia schema."
  [antlr-tree resolvers scalars documentation]
  (let [root (select [:graphqlSchema] [[antlr-tree]])]
    (-> {:objects (apply merge (select-map xform-type [#{:inputTypeDef :typeDef}] root))
         :enums (apply merge (select-map xform-enum [:enumDef] root))
         :scalars (apply merge (select-map xform-scalar [:scalarDef] root))
         :unions (apply merge (select-map xform-union [:unionDef] root))
         :interfaces (apply merge (select-map xform-type [:interfaceDef] root))}
        (attach-resolvers resolvers)
        (attach-scalars scalars)
        (attach-documentation documentation)
        (attach-operations root))))

(defn parse-schema
  "Given a GraphQL schema string, parses it and returns a Lacinia EDN
  schema. Defers validation of the schema to the downstream schema
  validator.

  `resolvers` is expected to be a map of:
  {type-name-k {field-name-keyword resolver-fn}}

  `scalars` is expected to be a map of:
  {scalar-name-k {:parse parse-fn
                  :serialize serialize-fn}}

  `documentation` is expected to be a map of:
  {type-name-k {:description doc-str
                :fields {field-name-k doc-str}}}"
  [schema-string resolvers scalars documentation]
  (remove-vals
   #(or (nil? %) (= {} %))
   (xform-schema (try
                   (antlr-parse grammar schema-string)
                   (catch ParseError e
                     (let [failures (parse-failures e)]
                       (throw (ex-info "Failed to parse GraphQL schema."
                                       {:errors failures})))))
                 resolvers
                 scalars
                 documentation)))

(s/def ::field-resolver (s/map-of simple-keyword? fn?))
(s/def ::resolver-map (s/map-of simple-keyword? ::field-resolver))
(s/def ::parse fn?)
(s/def ::serialize fn?)
(s/def ::scalar-def (s/keys :req-un [::parse ::serialize]))
(s/def ::scalar-map (s/map-of simple-keyword? ::scalar-def))
(s/def ::description string?)
(s/def ::fields (s/map-of simple-keyword? ::description))
(s/def ::documentation-def (s/keys :opt-un [::description ::fields]))
(s/def ::documentation-map (s/map-of simple-keyword? ::documentation-def))

(s/fdef parse-schema
        :args (s/cat :schema-string string?
                     :resolvers ::resolver-map
                     :scalars ::scalar-map
                     :documentation ::documentation-map))

(stest/instrument `parse-schema)
