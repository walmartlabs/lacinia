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

(ns com.walmartlabs.lacinia.parser.schema
  "Parse a Schema Definition Language document into a Lacinia input schema."
  {:added "0.22.0"}
  (:require
    [com.walmartlabs.lacinia.internal-utils :refer [remove-vals]]
    [com.walmartlabs.lacinia.parser.common :as common]
    [com.walmartlabs.lacinia.util :refer [inject-descriptions]]
    [clojure.spec.alpha :as s])
  (:import
    (clj_antlr ParseError)))

;; When using Clojure 1.8, the dependency on clojure-future-spec must be included,
;; and this code will trigger
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [simple-keyword?]]))

(def ^:private grammar
  (common/compile-grammar "com/walmartlabs/lacinia/schema.g4"))

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
  "Transforms a type specification parse tree node.

  Example node:
  ((:typeName (:name \"Character\")))
  or
  ((:listType (:typeSpec (:typeName (:name \"episode\")))))"
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
  "Transforms a default argument value parse tree node.

  Example node:
  ((:value
   (:objectValue
    (:objectField
     (:name \"name\")
     (:value (:stringvalue \"Unspecified\")))
    (:objectField
     (:name \"episodes\")
     (:value
      (:arrayValue
       (:value (:enumValue (:name \"NEWHOPE\")))
       (:value (:enumValue (:name \"EMPIRE\")))
       (:value (:enumValue (:name \"JEDI\")))))))))"
  [arg-value]
  (let [[type value & _] arg-value]
    (case type
      :nullvalue nil
      :enumValue (keyword (second value))
      :arrayValue (mapv (comp xform-default-value second) (rest arg-value))
      :objectValue (apply merge (select-map xform-map-value [:objectField] [(rest arg-value)]))
      :stringvalue (common/stringvalue->String value)
      :blockstringvalue (common/blockstringvalue->String value)
      value)))

(defn ^:private xform-map-value
  "Transforms a map value parse tree node.

  Example node:
  ((:name \"name\")
   (:value (:stringvalue \"Unspecified\")))"
  [object-field]
  {(keyword (select1 [:name] object-field))
   (some-> (select1 [:value] object-field)
           (xform-default-value))})

(defn ^:private xform-field-arg
  "Transforms an argument parse tree node.

  Example node:
  ((:name \"episode\")
   (:typeSpec (:typeName (:name \"episode\")))
   (:defaultValue (:value (:enumValue (:name \"NEWHOPE\")))))"
  [arg]
  {(keyword (select1 [:name] arg))
   (let [field-arg {:type (xform-typespec (select [:typeSpec] arg))}]
     (if-let [default-value (some-> (select1 [:defaultValue :value] arg)
                                    (xform-default-value))]
       (assoc field-arg :defaultValue default-value)
       field-arg))})

(defn ^:private xform-field
  "Transforms a field parse tree node.

  Example node:
  ((:fieldName (:name \"name\"))
   (:typeSpec (:typeName (:name \"String\"))))"
  [field]
  {(keyword (select1 [:fieldName :name] field))
   (cond-> {:type (xform-typespec (select [:typeSpec] field))}
     (select [:fieldArgs] field) (assoc :args
                                        (apply merge
                                               (select-map xform-field-arg [:fieldArgs :argument] field))))})

(defn ^:private xform-type
  "Transforms a type definition parse tree node.

  Example node:
  ((:'type' \"type\")
   (:typeName (:name \"CharacterOutput\"))
   (:implementationDef
    (:'implements' \"implements\")
    (:typeName (:name \"Human\"))
    (:typeName (:name \"Jedi\"))
   (:fieldDef
    (:fieldName (:name \"name\"))
    (:typeSpec (:typeName (:name \"String\"))))
   (:fieldDef
    (:fieldName (:name \"birthDate\"))
    (:typeSpec (:typeName (:name \"Date\")))))"
  [type]
  {(keyword (select1 [:typeName :name] type))
   (let [[_ _ maybe-impl-def] (first type)
         implemented-types (when (= :implementationDef (first maybe-impl-def))
                             (->> maybe-impl-def
                                  (drop 2)                  ; :implementationDef and :implements pair
                                  (map (comp keyword second second))))]
     (cond-> {:fields (apply merge (select-map xform-field [:fieldDef] type))}
       implemented-types (assoc :implements implemented-types)))})

(defn ^:private xform-enum
  "Transforms an enum parse tree node.

  Example node:
  ((:'enum' \"enum\")
   (:typeName (:name \"episode\"))
   (:scalarName (:name \"NEWHOPE\"))
   (:scalarName (:name \"EMPIRE\"))
   (:scalarName (:name \"JEDI\")))"
  [enum]
  {(keyword (select1 [:typeName :name] enum))
   {:values (vec (map #(hash-map :enum-value (-> % first keyword))
                      (select [:scalarName :name] enum)))}})

#_ (defn ^:private xform-operation
  "Transforms an operation parse tree node by inlining the operation types."
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
  "Transforms a scalar parse tree node.

  Example node:
  ((:'scalar' \"scalar\") (:typeName (:name \"Date\")))"
  [scalar]
  {(keyword (select1 [:typeName :name] scalar))
   {:parse nil
    :serialize nil}})

(defn ^:private xform-union
  "Transforms a union parse tree node.

  Example node:
  ((:'union' \"union\")
   (:typeName (:name \"Queries\"))
   (:unionTypes
    (:typeName (:name \"Query\"))
    (:'|' \"|\")
    (:typeName (:name \"OtherQuery\"))))"
  [union]
  {(keyword (select1 [:typeName :name] union))
   {:members (mapv (comp keyword first)
                   (select [:unionTypes :typeName :name] union))}})

(defn ^:private attach-operations
  "Builds the :schema key of the Lacinia schema.

  Example schema definition parse tree node:

  ((:schemaDef
    (:'schema' \"schema\")
    (:operationTypeDef
     (:queryOperationDef
      (:'query' \"query\")
      (:typeName (:name \"Queries\"))))
    (:operationTypeDef
     (:mutationOperationDef
      (:'mutation' \"mutation\")
      (:typeName (:name \"Mutation\"))))
    (:operationTypeDef
     (:subscriptionOperationDef
      (:'subscription' \"subscription\")
      (:typeName (:name \"Subscription\"))))))"
  [schema root]
  (->> (select [:schemaDef :operationTypeDef] root)
       (map #(vector (-> % first second second keyword)
                     (-> % first (nth 2) second second keyword)))
       (into {})
       (assoc schema :roots)))

(defn ^:private attach-field-fns
  "Attaches a map of either resolvers or subscription streamers"
  [schema fn-k fn-map]
  {:pre [(#{:resolve :stream} fn-k)]}
  (reduce-kv (fn [schema' type fields]
               (reduce-kv (fn [schema'' field f]
                            (assoc-in schema'' [:objects type :fields field fn-k] f))
                          schema'
                          fields))
             schema
             fn-map))

(defn ^:private attach-scalars
  [schema scalars]
  (cond-> schema
    scalars (assoc :scalars scalars)))

(defn ^:private duplicates
  "Returns duplicates in coll, retaining original element meta"
  [coll]
  (let [coll-freq (frequencies coll)]
    (->> (remove (fn [el] (= (get coll-freq el) 1)) coll)
         (seq))))

(defn ^:private validate!
  "Validates the schema parse tree against errors that will be hidden
  by the transformation to the Lacinia schema."
  [root]
  (when-let [errors (->> (concat
                          ;; Find duplicate types
                          (when-let [duplicate-types (->> root
                                                          (select [#{:typeDef :enumDef :scalarDef :unionDef :interfaceDef :inputTypeDef} :typeName])
                                                          (map first)
                                                          (duplicates))]
                            [{:error "Duplicate type names" :duplicate-types (map (fn [type-name-node]
                                                                                    {:name (second type-name-node)
                                                                                     :location (meta type-name-node)})
                                                                                  duplicate-types)}])
                          ;; find duplicate fields within each type
                          (select-map (fn [nodes]
                                        (when-let [duplicate-fields (->> nodes
                                                                         (select [:fieldDef :fieldName])
                                                                         (map first)
                                                                         (duplicates))]
                                          {:error "Duplicate fields defined on type"
                                           :duplicate-fields (map (fn [field-name-node]
                                                                    {:name (second field-name-node)
                                                                     :location (meta field-name-node)})
                                                                  duplicate-fields)
                                           :type (select1 [:typeName :name] nodes)}))
                                      [#{:typeDef :inputTypeDef :interfaceDef}]
                                      root)
                          ;; find duplicate arguments within each field
                          (select-map (fn [nodes]
                                        (when-let [duplicate-args (->> nodes
                                                                       (select [:fieldArgs :argument :name])
                                                                       (map first)
                                                                       (duplicates))]
                                          {:error "Duplicate arguments defined on field"
                                           :duplicate-arguments (distinct duplicate-args)
                                           :field (let [field-name-node (select1 [:fieldName] nodes)]
                                                    {:name (second field-name-node)
                                                     :location (meta field-name-node)})}))
                                      [#{:typeDef :interfaceDef} :fieldDef]
                                      root))
                         (remove nil?)
                         (seq))]
    (throw (ex-info "Error parsing schema" {:errors errors}))))

(defn ^:private xform-schema
  "Given an ANTLR parse tree, returns a Lacinia schema."
  [antlr-tree resolvers scalars streamers documentation]
  (let [root (select [:graphqlSchema] [[antlr-tree]])]
    (validate! root)
    (-> {:objects (apply merge (select-map xform-type [:typeDef] root))
         :input-objects (apply merge (select-map xform-type [:inputTypeDef] root))
         :enums (apply merge (select-map xform-enum [:enumDef] root))
         :scalars (apply merge (select-map xform-scalar [:scalarDef] root))
         :unions (apply merge (select-map xform-union [:unionDef] root))
         :interfaces (apply merge (select-map xform-type [:interfaceDef] root))}
        (attach-field-fns :resolve resolvers)
        (attach-field-fns :stream streamers)
        (attach-scalars scalars)
        (inject-descriptions documentation)
        (attach-operations root))))

(defn parse-schema
  "Given a GraphQL schema string, parses it and returns a Lacinia EDN
  schema. Defers validation of the schema to the downstream schema
  validator.

  `attach` should be a map consisting of the following keys:

  `:resolvers` is expected to be a map of:
  {:type-name {:field-name resolver-fn}}

  `:scalars` is expected to be a map of:
  {:scalar-name {:parse parse-spec
                 :serialize serialize-spec}}

  `:streamers` is expected to be a map of:
  {:type-name {:subscription-field-name stream-fn}}

  `:documentation` is expected to be a map of:
  {:type-name doc-str
   :type-name/field-name doc-str
   :type-name.field-name/arg-name doc-str}"
  [schema-string attach]
  (when-let [ed (s/explain-data ::parse-schema-args [schema-string attach])]
    (throw (ex-info (str "Arguments to parse-schema do not conform to spec:\n" (with-out-str (s/explain-out ed)))
                    ed)))

  (let [{:keys [resolvers scalars streamers documentation]} attach]
    (remove-vals ;; Remove any empty schema components to avoid clutter
     ;; and optimize for human readability
     #(or (nil? %) (= {} %))
     (xform-schema (try
                     (common/antlr-parse grammar schema-string)
                     (catch ParseError e
                       (let [failures (common/parse-failures e)]
                         (throw (ex-info "Failed to parse GraphQL schema."
                                         {:errors failures})))))
                   resolvers
                   scalars
                   streamers
                   documentation))))

(s/def ::field-fn (s/map-of simple-keyword? (s/or :function fn? :keyword simple-keyword?)))
(s/def ::fn-map (s/map-of simple-keyword? ::field-fn))
(s/def ::parse s/spec?)
(s/def ::serialize s/spec?)
(s/def ::scalar-def (s/keys :req-un [::parse ::serialize]))
(s/def ::description string?)
(s/def ::fields (s/map-of simple-keyword? ::description))

(s/def ::documentation (s/map-of keyword? string?))
(s/def ::scalars (s/map-of simple-keyword? ::scalar-def))
(s/def ::resolvers ::fn-map)
(s/def ::streamers ::fn-map)

(s/def ::parse-schema-args (s/cat :schema-string string?
                                  :attachments (s/keys :opt-un [::resolvers
                                                                ::streamers
                                                                ::scalars
                                                                ::documentation])))
