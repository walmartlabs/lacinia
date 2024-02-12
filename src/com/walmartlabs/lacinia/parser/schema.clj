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
    #_[io.pedestal.log :as log]
    [com.walmartlabs.lacinia.internal-utils :refer [remove-vals keepv q qualified-name]]
    [com.walmartlabs.lacinia.parser.common :as common]
    [com.walmartlabs.lacinia.util :refer [inject-descriptions]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.federation :as federation]
    [clojure.spec.alpha :as s]
    [clojure.string :as str])
  (:import (com.walmartlabs.lacinia GraphqlSchemaLexer GraphqlSchemaParser ParseError)))

;; When using Clojure 1.8, the dependency on clojure-future-spec must be included,
;; and this code will trigger
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [simple-keyword?]]))

(def ^:private extension-meta {:extension true})

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

(defn is-extension?
  [v]
  (get (meta v) :extension false))

(defn ^:private merge-extension
  [k v org]
  (doseq [property (keys (get org :fields {}))]
    (when (contains? (get v :fields {}) property)
      (let [locations (keepv meta [org v])]
        (throw (ex-info (format "Field %s already defined in the existing schema. It cannot also be defined in this type extension."
                                (q (qualified-name k property)))
                        (cond-> {:key k}
                          (seq locations) (assoc :locations locations)))))))
  (doseq [member (get org :members [])]
    (when (contains? (set (:members v)) member)
      (let [locations (keepv meta [org v])]
        (throw (ex-info (format "%s already member of union %s in the existing schema. It cannot also be defined in this union extension."
                                (q member) (q k))
                        (cond-> {:key k
                                 :member member}
                                (seq locations) (assoc :locations locations)))))))
  (reduce merge
          {}
          [(some->> (merge (get org :fields {}) (get v :fields {}))
                    (#(if (not-empty %) % nil))
                    (assoc {} :fields))
           (some->> (into (get org :members []) (get v :members []))
                    (vec)
                    (#(if (not-empty %) % nil))
                    (assoc {} :members))
           (some->> (into (get org :implements []) (get v :implements []))
                    (distinct)
                    (vec)
                    (#(if (not-empty %) % nil))
                    (assoc {} :implements))
           ; TODO check and/or remove duplicate directives
           (some->> (into (get org :directives []) (get v :directives []))
                    (#(if (not-empty %) % nil))
                    (assoc {} :directives))
           (some->> [(get org :description) (get v :description)]
                    (remove nil?)
                    (str/join " ")
                    (#(if (not-empty %) % nil))
                    (assoc {} :description))]))

(defn ^:private assoc-check
  [m content k v]
  (cond (and (not (contains? m k))
             (is-extension? v))
        (let [locations (keepv meta [(get m k) v])]
          (throw (ex-info (format "Cannot extend type %s because it does not exist in the existing schema."
                                  (q k))
                          (cond-> {:key k}
                            (seq locations) (assoc :locations locations)))))

        (not (contains? m k))
        (assoc m k v)

        (is-extension? v)
        (update m k (partial merge-extension k v))

        (contains? m k)
        (let [locations (keepv meta [(get m k) v])]
          (throw (ex-info (format "Conflicting %s: %s."
                                  content
                                  (q k))
                          (cond-> {:key k}
                            (seq locations) (assoc :locations locations)))))))

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

(defn ^:private apply-directives
  [element directiveList]
  (if directiveList
    (assoc element :directives (xform directiveList))
    element))

(defmethod xform :schemaDef
  [prod]
  (let [sub-prods (drop 2 prod)
        directiveList (when (= :directiveList (-> sub-prods first first))
                        (first sub-prods))
        remaining-prods (if directiveList
                          (rest sub-prods)
                          sub-prods)]
    [[:roots] (-> (checked-map "schema entry" (map xform remaining-prods))
                  ;; Temporary location until patch-schema-directives is invoked
                  (apply-directives directiveList))]))

(defn ^:private patch-schema-directives
  [schema]
  (if-let [directives (get-in schema [:roots :directives])]
    (-> schema
        (assoc :directives directives)
        (update :roots dissoc :directives))
    schema))

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

(defmethod xform :anyName
  [prod]
  (xform-second prod))

(defmethod xform :nameTokens
  [prod]
  (xform (list :name (-> prod second second))))

(defmethod xform :description
  [prod]
  (xform-second prod))

(defmethod xform :typeDef
  [prod]
  (let [{:keys [anyName implementationDef fieldDefs description directiveList]
         :or {fieldDefs (list :fieldDefs)}} (tag prod)]
    [[:objects (xform anyName)]
     (-> {:fields (xform fieldDefs)}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList)
         (cond-> implementationDef (assoc :implements (xform implementationDef))))]))

(defmethod xform :typeExtDef
  [prod]
  (let [{:keys [anyName implementationDef fieldDefs description directiveList]
         :or {fieldDefs (list :fieldDefs)}} (tag prod)]
    (with-meta [[:objects (xform anyName)]
                (-> {:fields (xform fieldDefs)}
                    (common/copy-meta anyName)
                    (common/add-meta extension-meta)
                    (apply-description description)
                    (apply-directives directiveList)
                    (cond-> implementationDef (assoc :implements (xform implementationDef))))]
               extension-meta)))

(defmethod xform :directiveDef
  [prod]
  (let [{:keys [anyName argList directiveLocationList description]} (tag prod)]
    [[:directive-defs (xform anyName)]
     (-> {:locations (xform directiveLocationList)}
         (common/copy-meta anyName)
         (apply-description description)
         (cond->
           argList (assoc :args (xform argList))))]))

(defmethod xform :directiveLocationList
  [prod]
  (->> prod
       rest
       (filter #(-> % first (= :directiveLocation)))
       (map xform)
       set))

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
  (let [{:keys [anyName directiveArgList]} (tag prod)]
    (-> {:directive-type (xform anyName)}
        (common/copy-meta anyName)
        (cond->
          directiveArgList (assoc :directive-args (xform directiveArgList))))))

(defmethod xform :directiveArgList
  [prod]
  (checked-map "directive argument"
               (->> prod
                    rest
                    (map xform))))

(defmethod xform :directiveArg
  [prod]
  (let [[_ k v] prod]
    [(xform k) (xform v)]))

(defmethod xform :fieldDefs
  [prod]
  (checked-map "field" (map xform (rest prod))))

(defmethod xform :fieldDef
  [prod]
  (let [{:keys [anyName typeSpec argList description directiveList]} (tag prod)]
    [(xform anyName)
     (-> {:type (xform typeSpec)}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList)
         (cond->
           argList (assoc :args (xform argList))))]))

(defmethod xform :argList
  [prod]
  (checked-map "field argument" (map xform (rest prod))))

(defmethod xform :argument
  [prod]
  (let [{:keys [anyName typeSpec defaultValue description directiveList]} (tag prod)]
    [(xform anyName)
     (-> {:type (xform typeSpec)}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList)
         (cond-> defaultValue (assoc :default-value (xform-second defaultValue))))]))

(defmethod xform :value
  [prod]
  (xform-second prod))

(defmethod xform :enumValue
  [prod]
  (xform-second prod))

(defmethod xform :booleanValue
  [prod]
  (let [v (-> prod second second)]
    (Boolean/valueOf ^String v)))

(defmethod xform :nullValue
  [_]
  nil)

(defmethod xform :intvalue
  [prod]
  (Integer/parseInt ^String (second prod)))

(defmethod xform :floatvalue
  [prod]
  (Float/parseFloat ^String (second prod)))

(defmethod xform :implementationDef
  [prod]
  (->> prod
       rest
       (filter #(-> % first (= :name)))
       (mapv xform)))

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
  (let [{:keys [anyName fieldDefs description directiveList]
         :or {fieldDefs (list :fieldDefs)}} (tag prod)]
    [[:interfaces (xform anyName)]
     (-> {:fields (xform fieldDefs)}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList))]))

(defmethod xform :unionDef
  [prod]
  (let [{:keys [description anyName unionTypes directiveList]} (tag prod)]
    [[:unions (xform anyName)]
     (-> {:members (xform unionTypes)}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList))]))

(defmethod xform :unionExtDef
  [prod]
  (let [{:keys [description anyName unionTypes directiveList]} (tag prod)]
    (with-meta [[:unions (xform anyName)]
                (-> {:members (xform unionTypes)}
                    (common/copy-meta anyName)
                    (common/add-meta extension-meta)
                    (apply-description description)
                    (apply-directives directiveList))]
               extension-meta)))

(defmethod xform :unionTypes
  [prod]
  (->> prod
       rest
       (filter #(-> % first (= :anyName)))
       (mapv xform)))

(defmethod xform :enumDef
  [prod]
  (let [{:keys [description anyName enumValueDefs directiveList]} (tag prod)]
    [[:enums (xform anyName)]
     (-> {:values (xform enumValueDefs)}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList))]))

(defmethod xform :enumValueDefs
  [prod]
  (mapv xform (rest prod)))

(defmethod xform :enumValueDef
  [prod]
  (let [{:keys [description nameTokens directiveList]} (tag prod)]
    (-> {:enum-value (xform nameTokens)}
        (common/copy-meta nameTokens)
        (apply-description description)
        (apply-directives directiveList))))

(defmethod xform :inputTypeDef
  [prod]
  (let [{:keys [anyName inputValueDefs description directiveList]
         :or {inputValueDefs (list :inputValueDef)}} (tag prod)]
    [[:input-objects (xform anyName)]
     (-> {:fields (xform inputValueDefs)}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList))]))

(defmethod xform :inputTypeExtDef
  [prod]
  (let [{:keys [anyName inputValueDefs description directiveList]
         :or {inputValueDefs (list :inputValueDefs)}} (tag prod)]
    (with-meta [[:input-objects (xform anyName)]
                (-> {:fields (xform inputValueDefs)}
                    (common/copy-meta anyName)
                    (common/add-meta extension-meta)
                    (apply-description description)
                    (apply-directives directiveList))]
               extension-meta)))


(defmethod xform :inputValueDefs
  [prod]
  (checked-map "input value" (map xform (rest prod))))

(defmethod xform :inputValueDef
  [prod]
  (let [{:keys [anyName typeSpec defaultValue description directiveList]} (tag prod)]
    [(xform anyName)
     (-> {:type (xform typeSpec)}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList)
         (cond-> defaultValue (assoc :default-value (xform-second defaultValue))))]))


(defmethod xform :scalarDef
  [prod]
  (let [{:keys [anyName description directiveList]} (tag prod)]
    [[:scalars (xform anyName)]
     (-> {}
         (common/copy-meta anyName)
         (apply-description description)
         (apply-directives directiveList))]))

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
  [antlr-tree empty-schema resolvers scalars streamers documentation]
  (let [schema (->> antlr-tree
                    rest
                    (map xform)
                    (sort-by (fn [x] (if (is-extension? x) 1 0)))
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
                            empty-schema))]
    (-> schema
        (attach-field-fns :resolve resolvers)
        (attach-field-fns :stream streamers)
        ;; TODO: This should inject stuff into the scalar, not replace it, right?
        (attach-scalars scalars)
        (inject-descriptions documentation)
        patch-schema-directives)))

(defn parse-schema
  "Given a GraphQL schema string, parses it and returns a Lacinia EDN
  schema. Defers validation of the schema to the downstream schema
  validator.

  Directives may be declared and used, but validation of directives is
  also deferred downstream.

  Most keys of the `attach` map are deprecated, but still supported.
  Documentation can now be provided inline in the schema document,
  and directives, streamers, and etc. can be added as needed via
  functions in the [[com.walmartlabs.lacinia.util]] namespace.

  `attach` should be a map consisting of the following keys:

  `:federation` enables support for GraphQL federation. It contains a
  sub-key, `:entity-resolvers` which maps from keyword entity name to
  an entity resolver function (or FieldResolver instance).

  `:resolvers` (deprecated) is expected to be a map of:
  {:type-name {:field-name resolver-fn}}

  `:scalars` (deprecated) is expected to be a map of:
  {:scalar-name {:parse parse-spec
                 :serialize serialize-spec}}

  A scalar map may also include a :description.

  The provided scalar maps are merged into the scalars parsed from the document.

  `:streamers` (deprecated) is expected to be a map of:
  {:type-name {:subscription-field-name stream-fn}}

  `:documentation` (deprecated) is expected to be a map of:
  {:type-name doc-str
   :type-name/field-name doc-str
   :type-name/field-name.arg-name doc-str}"
  ([schema-string]
   (parse-schema schema-string {}))
  ([schema-string attach]
   (when-let [ed (s/explain-data ::parse-schema-args [schema-string attach])]
     (throw (ex-info (str "Arguments to parse-schema do not conform to spec:\n" (with-out-str (s/explain-out ed)))
                     ed)))

   (let [{:keys [resolvers scalars streamers documentation federation]} attach
         empty-schema (if federation
                        federation/foundation-types
                        {})
         antlr-tree (try
                      (let [ap (reify common/AntlrParser
                                 (lexer [_ char-stream]
                                   (GraphqlSchemaLexer. char-stream))
                                 (parser [_ token-stream]
                                   (GraphqlSchemaParser. token-stream))
                                 (tree [_ parser]
                                   (.graphqlSchema ^GraphqlSchemaParser parser)))]
                        (common/antlr-parse ap schema-string))
                      (catch ParseError e
                        (let [failures (common/parse-failures e)]
                          (throw (ex-info "Failed to parse GraphQL schema."
                                          {:errors failures})))))]
     (cond-> (xform-schema antlr-tree
                           empty-schema
                           resolvers
                           scalars
                           streamers
                           documentation)
       federation (federation/inject-federation schema-string
                                                (:entity-resolvers federation))))))

(s/def ::field-fn (s/map-of simple-keyword? (s/or :function ::schema/function-or-var
                                                  :keyword simple-keyword?)))
(s/def ::fn-map (s/map-of simple-keyword? ::field-fn))
(s/def ::parse ::schema/parse-or-serialize-fn)
(s/def ::serialize ::schema/parse-or-serialize-fn)
(s/def ::scalar-def (s/keys :req-un [::parse ::serialize]
                            :opt-un [::description]))
(s/def ::description string?)

(s/def ::documentation (s/map-of keyword? string?))
(s/def ::scalars (s/map-of simple-keyword? ::scalar-def))
(s/def ::resolvers ::fn-map)
(s/def ::streamers ::fn-map)

(s/def ::federation (s/keys :req-un [::federation/entity-resolvers]))

(s/def ::parse-schema-args (s/or
                             :supported string?
                             :attach (s/cat :schema-string string?
                                            :attachments (s/keys :opt-un [::resolvers
                                                                          ::streamers
                                                                          ::scalars
                                                                          ::documentation
                                                                          ::federation]))))
