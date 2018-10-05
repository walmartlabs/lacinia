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
    [io.pedestal.log :as log]
    [com.walmartlabs.lacinia.internal-utils :refer [remove-vals keepv q]]
    [com.walmartlabs.lacinia.parser.common :as common]
    [com.walmartlabs.lacinia.util :refer [inject-descriptions]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str])
  (:import
    (clj_antlr ParseError)))

;; When using Clojure 1.8, the dependency on clojure-future-spec must be included,
;; and this code will trigger
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [simple-keyword?]]))

(def ^:private grammar
  (common/compile-grammar "com/walmartlabs/lacinia/schema.g4"))

(defn ^:private tag
  "Returns a map of the nested productions in a production.
  Nested productions are elements after the first; the key is the
  first element in each nested production.

  Note that this should only be used in cases where none of the productions repeat."
  [prod]
  (reduce (fn [m p]
            (assoc m (first p) p))
          {}
          (rest prod)))

(defn ^:private assoc-check
  [m content k v]
  (when (contains? m k)
    (let [locations (keepv meta [(get m k)
                                 v])]
      (throw (ex-info (format "Conflicting %s: %s."
                              content
                              (q k))
                      (cond-> {:key k}
                        (seq locations) (assoc :locations locations))))))
  (assoc m k v))

(defn ^:private checked-map
  "Given a seq of key/value tuples, assembles a map, checking that keys do not conflict
  (throwing an exception if they do).

  content describes what is being built, and is used for exception messages."
  [content kvs]
  (reduce (fn [m [k v]]
            (assoc-check m content k v))
          {}
          kvs))

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
  (if (seq scalars)
    (update schema :scalars
            (fn [s]
              (reduce-kv (fn [s' scalar-name scalar-value]
                           (update s' scalar-name merge scalar-value))
                         s
                         scalars)))
    schema))

;; This is very similar to the code for parsing a query, and includes a bit of duplication.
;; Perhaps at some point we can merge it all into a single, unified grammar.

(defmulti ^:private xform
  "Transform an Antlr production into a result.

  Antlr productions are recursive lists; the first element is a type
  (from the grammar), and the rest of the list are nested productions.

  Meta data on the production is the location (line, column) of the production."
  first
  ;; When debugging/developing, this is incredibly useful:
  #_(fn [prod]
      (log/trace :dispatch prod)
      (first prod))
  :default ::default)

(defn ^:private xform-second
  [prod]
  (-> prod second xform))

(defn ^:private apply-description
  [parsed descripion-prod]
  (cond-> parsed
    descripion-prod (assoc :description (xform descripion-prod))))

(defmethod xform :schemaDef
  [prod]
  [[:roots] (checked-map "schema entry" (map xform (drop 2 prod)))])

(defmethod xform :operationTypeDef
  [prod]
  (xform (second prod)))

(defmethod xform :queryOperationDef
  [prod]
  (let [[_ _ type-prod] prod]
    [:query (xform type-prod)]))

(defmethod xform :mutationOperationDef
  [prod]
  (let [[_ _ type-prod] prod]
    [:mutation (xform type-prod)]))

(defmethod xform :subscriptionOperationDef
  [prod]
  (let [[_ _ type-prod] prod]
    [:subscription (xform type-prod)]))

(defmethod xform :typeName
  [prod]
  (let [name-k (xform-second prod)]
    ;; By convention, these type names for built-in types are represented as
    ;; symbols, not keywords.
    (if (#{:Boolean :String :Int :Float :ID} name-k)
      (-> name-k name symbol)
      name-k)))

(defmethod xform :name
  [prod]
  (-> prod second keyword))

(defmethod xform :description
  [prod]
  (xform-second prod))

(defmethod xform :typeDef
  [prod]
  (let [{:keys [name implementationDef fieldDefs description]} (tag prod)]
    [[:objects (xform name)]
     (-> {:fields (xform fieldDefs)}
         (common/copy-meta name)
         (apply-description description)
         (cond-> implementationDef (assoc :implements (xform implementationDef))))]))

(defmethod xform :directiveDef
  [prod]
  (let [{:keys [name argList directiveLocationList description]} (tag prod)]
    [[:directive-defs (xform name)]
     (-> {:locations (xform directiveLocationList)}
         (common/copy-meta name)
         (apply-description description)
         (cond->
           argList (assoc :args (xform argList))))]))

(defmethod xform :directiveLocationList
  [prod]
  (->> prod
       rest
       (filter #(-> % first (= :directiveLocation)))
       (map xform)))

(defn ^:private directive-name->keyword
  [s]
  (-> s
      str/lower-case
      (str/replace "_" "-")
      keyword))

(defmethod xform :directiveLocation
  [prod]
  (-> prod
      second
      second
      second
      directive-name->keyword))

(defmethod xform :directiveList
  [prod]
  (mapv xform (rest prod)))

(defmethod xform :directive
  [prod]
  (let [{:keys [name directiveArgList]} (tag prod)]
    (-> {:type (xform name)}
        (common/copy-meta name)
        (cond->
          directiveArgList (assoc :directive-args (xform directiveArgList))))))

(defmethod xform :directiveArgList
  [prod]
  (reduce (fn [m valueProd]
            (let [[_ name value] valueProd]
              (assoc m (xform name) (xform value))))
          {}
          (rest prod)))

(defmethod xform :fieldDefs
  [prod]
  (checked-map "field" (map xform (rest prod))))

(defmethod xform :fieldDef
  [prod]
  (let [{:keys [name typeSpec argList description directiveList]} (tag prod)]
    [(xform name)
     (-> {:type (xform typeSpec)}
         (common/copy-meta name)
         (apply-description description)
         (cond->
           argList (assoc :args (xform argList))
           directiveList (assoc :directives (xform directiveList))))]))

(defmethod xform :argList
  [prod]
  (checked-map "field argument" (map xform (rest prod))))

(defmethod xform :argument
  [prod]
  (let [{:keys [name typeSpec defaultValue description]} (tag prod)]
    [(xform name)
     (-> {:type (xform typeSpec)}
         (common/copy-meta name)
         (apply-description description)
         (cond-> defaultValue (assoc :default-value (xform-second defaultValue))))]))

(defmethod xform :value
  [prod]
  (xform-second prod))

(defmethod xform :enumValue
  [prod]
  (xform-second prod))

(defmethod xform :booleanvalue
  [prod]
  (Boolean/valueOf ^String (second prod)))

(defmethod xform :intvalue
  [prod]
  (Integer/parseInt ^String (second prod)))

(defmethod xform :floatvalue
  [prod]
  (Float/parseFloat ^String (second prod)))

(defmethod xform :implementationDef
  [prod]
  (let [types (drop 2 prod)]
    (mapv xform types)))

(defmethod xform :typeSpec
  [prod]
  (let [[_ type required] prod
        base-type (-> type xform)]
    (if (some? required)
      (list 'non-null base-type)
      base-type)))

(defmethod xform :listType
  [prod]
  (list 'list (xform-second prod)))

(defmethod xform :interfaceDef
  [prod]
  (let [{:keys [name fieldDefs description]} (tag prod)]
    [[:interfaces (xform name)]
     (-> {:fields (xform fieldDefs)}
         (common/copy-meta name)
         (apply-description description))]))

(defmethod xform :unionDef
  [prod]
  (let [{:keys [description name unionTypes]} (tag prod)]
    [[:unions (xform name)]
     (-> {:members (xform unionTypes)}
         (common/copy-meta name)
         (apply-description description))]))

(defmethod xform :unionTypes
  [prod]
  (->> prod
       rest
       (filter #(-> % first (= :name)))
       (mapv xform)))

(defmethod xform :enumDef
  [prod]
  (let [{:keys [description name enumValueDefs]} (tag prod)]
    [[:enums (xform name)]
     (-> {:values (xform enumValueDefs)}
         (common/copy-meta name)
         (apply-description description))]))

(defmethod xform :enumValueDefs
  [prod]
  (mapv xform (rest prod)))

(defmethod xform :enumValueDef
  [prod]
  (let [{:keys [description name]} (tag prod)]
    (-> {:enum-value (xform name)}
        (common/copy-meta name)
        (apply-description description))))

(defmethod xform :inputTypeDef
  [prod]
  (let [{:keys [name fieldDefs description]} (tag prod)]
    [[:input-objects (xform name)]
     (-> {:fields (xform fieldDefs)}
         (common/copy-meta name)
         (apply-description description))]))

(defmethod xform :scalarDef
  [prod]
  (let [{:keys [name description]} (tag prod)]
    [[:scalars (xform name)]
     (-> {}
         (common/copy-meta name)
         (apply-description description))]))

(defmethod xform :objectValue
  [prod]
  (-> (mapv xform (rest prod))
      (as-> % (checked-map "object key" %))
      (common/copy-meta prod)))

(defmethod xform :objectField
  [prod]
  (let [[_ name value] prod]
    [(xform name)
     (xform value)]))

(defmethod xform :stringvalue
  [prod]
  (-> prod second common/stringvalue->String))

(defmethod xform :blockstringvalue
  [prod]
  (-> prod second common/blockstringvalue->String))

(defmethod xform :arrayValue
  [prod]
  (common/copy-meta (mapv xform (rest prod)) prod))

(defn ^:private xform-schema
  "Given an ANTLR parse tree, returns a Lacinia schema."
  [antlr-tree resolvers scalars streamers documentation]
  (let [schema (->> antlr-tree
                    rest
                    (map xform)
                    (reduce (fn [schema [path value]]
                              (let [path' (butlast path)
                                    k (last path)]
                                ;; Generally, the path is two values (a category such
                                ;; as :objects, and a key within), but there's also
                                ;; [:root] (for the schema production).
                                (if-not (seq path')
                                  (assoc schema k value)
                                  (update-in schema path'
                                             assoc-check
                                             (-> path' last name) k value))))
                            {}))]
    (-> schema
        (attach-field-fns :resolve resolvers)
        (attach-field-fns :stream streamers)
        ;; TODO: This should inject stuff into the scalar, not replace it, right?
        (attach-scalars scalars)
        (inject-descriptions documentation))))

(defn parse-schema
  "Given a GraphQL schema string, parses it and returns a Lacinia EDN
  schema. Defers validation of the schema to the downstream schema
  validator.

  Directives may be declared and used, but validation of directives is
  also deferred downstream.

  `attach` should be a map consisting of the following keys:

  `:resolvers` is expected to be a map of:
  {:type-name {:field-name resolver-fn}}

  `:scalars` is expected to be a map of:
  {:scalar-name {:parse parse-spec
                 :serialize serialize-spec}}

  A scalar map may also include a :description.

  The provided scalar maps are merged into the scalars parsed from the document.

  `:streamers` is expected to be a map of:
  {:type-name {:subscription-field-name stream-fn}}

  `:documentation` is expected to be a map of:
  {:type-name doc-str
   :type-name/field-name doc-str
   :type-name/field-name.arg-name doc-str}"
  [schema-string attach]
  (when-let [ed (s/explain-data ::parse-schema-args [schema-string attach])]
    (throw (ex-info (str "Arguments to parse-schema do not conform to spec:\n" (with-out-str (s/explain-out ed)))
                    ed)))

  (let [{:keys [resolvers scalars streamers documentation]} attach]
    (xform-schema (try
                    (common/antlr-parse grammar schema-string)
                    (catch ParseError e
                      (let [failures (common/parse-failures e)]
                        (throw (ex-info "Failed to parse GraphQL schema."
                                        {:errors failures})))))
                  resolvers
                  scalars
                  streamers
                  documentation)))

(s/def ::field-fn (s/map-of simple-keyword? (s/or :function fn? :keyword simple-keyword?)))
(s/def ::fn-map (s/map-of simple-keyword? ::field-fn))
(s/def ::parse s/spec?)
(s/def ::serialize s/spec?)
(s/def ::scalar-def (s/keys :req-un [::parse ::serialize]
                            :opt-un [::description]))
(s/def ::description string?)

(s/def ::documentation (s/map-of keyword? string?))
(s/def ::scalars (s/map-of simple-keyword? ::scalar-def))
(s/def ::resolvers ::fn-map)
(s/def ::streamers ::fn-map)

(s/def ::parse-schema-args (s/cat :schema-string string?
                                  :attachments (s/keys :opt-un [::resolvers
                                                                ::streamers
                                                                ::scalars
                                                                ::documentation])))
