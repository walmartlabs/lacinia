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

(ns ^:no-doc com.walmartlabs.lacinia.parser.query
  "Uses the Antlr grammar to parse a document into the intermediate format.

  Note that the intermediate format is designed to be something that can be easily generated from
  Anltr (on the JVM) or with some other parsing library (in-browser, or Node)."
  {:added "0.26.0"}
  (:require
    #_[io.pedestal.log :as log]
    #_[clojure.pprint :as pprint]
    [com.walmartlabs.lacinia.parser.antlr :refer [AntlrParser]]
    [com.walmartlabs.lacinia.parser.common :as common])
  (:import
    (com.walmartlabs.lacinia GraphqlParser GraphqlLexer ParseError)))

(set! *warn-on-reflection* true)

(defmulti ^:private xform
  "Transform an Antlr production into a result.

  Antlr productions are recursive lists; the first element is a type
  (from the grammar), and the rest of the list are nested productions."
  first
  ;; When debugging/developing, this is incredibly useful:
  #_(fn [prod]
    (log/trace :dispatch prod)
    (first prod))
  :default ::default)

(defmethod xform :argument
  [prod]
  (let [[_ name value] prod]
    {:arg-name (xform name)
     :arg-value (-> value second xform)}))

(defmethod xform :definition
  [prod]
  (xform (second prod)))

(defmethod xform :operationDefinition
  [prod]
  (let [{:keys [operationType selectionSet variableDefinitions directives]
         op-name :name} (common/as-map prod)
        type (if operationType
               (-> operationType first xform)
               :query)]
    (cond-> (common/copy-meta {:type type} prod)

      op-name (assoc :name (-> op-name first xform))

      selectionSet (assoc :selections (mapv xform selectionSet))

      directives (assoc :directives (mapv xform directives))

      variableDefinitions (assoc :vars (mapv xform variableDefinitions)))))

(defmethod xform :variableDefinition
  [prod]
  (let [[_ var-name var-type var-default] prod]
    (cond-> {:var-name (-> var-name second xform)
             :var-type (xform var-type)}
      var-default (assoc :default (-> var-default second second xform)))))

(defmethod xform :type
  [prod]
  (-> prod second xform))

(defmethod xform :nonNullType
  [prod]
  {:type :non-null
   :of-type (-> prod second xform)})

(defmethod xform :listType
  [prod]
  {:type :list
   :of-type (-> prod second xform)})

(defmethod xform :typeName
  [prod]
  {:type :root-type
   :type-name (-> prod second second xform)})

(defmethod xform :selection
  [prod]
  (-> prod second xform))

(defmethod xform :field
  [prod]
  (let [{:keys [name selectionSet alias arguments directives]} (common/as-map prod)]
    (cond->
      (common/copy-meta {:type :field
                  :field-name (xform (first name))}
                        prod)

      alias (assoc :alias (xform (first alias)))

      selectionSet (assoc :selections (mapv xform selectionSet))

      directives (assoc :directives (mapv xform directives))

      arguments (assoc :args (mapv xform arguments)))))

(defmethod xform :nameid
  [prod]
  (-> prod second keyword))

(defmethod xform :name
  [[_ [_ value]]]
  (keyword value))

(defmethod xform :operationType
  [prod]
  (-> prod second xform))

(defmethod xform :'query'
  [_]
  :query)

(defmethod xform :'mutation'
  [_]
  :mutation)

(defmethod xform :'subscription'
  [_]
  :subscription)

(defmethod xform :booleanvalue
  [prod]
  {:type :boolean
   :value (second prod)})

(defmethod xform :intvalue
  [prod]
  {:type :integer
   :value (second prod)})

(defmethod xform :floatvalue
  [prod]
  {:type :float
   :value (second prod)})

(defmethod xform :stringvalue
  [prod]
  {:type :string
   ;; The value from Antlr has quotes around it that need to be stripped off.
   :value (-> prod second common/stringvalue->String)})

(defmethod xform :blockstringvalue
  [prod]
  {:type :string
   :value (-> prod second common/blockstringvalue->String)})

(defmethod xform :arrayValue
  [prod]
  {:type :array
   :value (->> prod
               rest
               (mapv (comp xform second)))})

(defmethod xform :nullvalue
  [_]
  {:type :null})

(defmethod xform :enumValue
  [prod]
  {:type :enum
   :value (-> prod second xform)})

(defmethod xform :objectValue
  [prod]
  {:type :object
   :value (->> prod
               rest
               (mapv xform))})

(defmethod xform :objectField
  [prod]
  (let [[_ arg-name arg-value] prod]
    {:arg-name (-> arg-name xform)
     :arg-value (-> arg-value second xform)}))

(defmethod xform :variable
  [prod]
  {:type :variable
   :value (-> prod second xform)})

(defmethod xform :inlineFragment
  [prod]
  (let [{:keys [typeCondition directives selectionSet]} (common/as-map prod)
        on-type (-> typeCondition first second)]
    (-> {:type :inline-fragment
         :on-type (xform on-type)
         :selections (mapv xform selectionSet)}
        (cond-> directives (assoc :directives (mapv xform directives)))
        (common/copy-meta on-type))))

(defmethod xform :fragmentSpread
  [prod]
  (let [[_ fragment-name directives] prod
        name (second fragment-name)]
    (-> {:type :named-fragment
         :fragment-name (xform name)}
        (cond-> directives (assoc :directives (mapv xform (rest directives))))
        (common/copy-meta name))))

(defmethod xform :fragmentDefinition
  [prod]
  (let [[_ fragment-name type-condition selection-set] prod
        name (second fragment-name)]
    (common/copy-meta
      {:type :fragment-definition
       :fragment-name (xform name)
       :on-type (-> type-condition second second xform)
       :selections (->> selection-set rest (mapv xform))}
      name)))

(defmethod xform :directive
  [prod]
  (let [[_ directive-name arguments] prod]
    (cond-> {:directive-name (xform directive-name)}
      arguments (assoc :args (->> arguments rest (mapv xform))))))

(defn ^:private xform-query
  [antlr-tree]
  #_(do
      (println "Parsed Antlr Tree:")
      (pprint/write antlr-vtree)
      (println))
  (let [top-levels (rest antlr-tree)]
    (mapv xform top-levels)))


(defn parse-query
  "Parses an input document (a string) into the intermediate query structure.

  Returns a vector of root definitions (:type is :fragment-definition,
  :query, :mutation, or :subscription). Continues from there.

  Currently, the overall structure is best described by the tests.

  Many nodes have meta data of keys :line and :column to describe thier location
  in the input source (used for error reporting)."
  [input]
  (xform-query
    (try
      (let [ap (reify AntlrParser
                 (lexer [_ char-stream]
                   (GraphqlLexer. char-stream))
                 (parser [_ token-stream]
                   (GraphqlParser. token-stream))
                 (tree [_ parser]
                   (.document ^GraphqlParser parser)))]
        (common/antlr-parse ap input))
      (catch ParseError e
        (let [failures (common/parse-failures e)]
          (throw (ex-info "Failed to parse GraphQL query."
                          {:errors failures})))))))
