(ns com.walmartlabs.lacinia.parser.query
  "Uses the Antlr grammar to parse a document into the intermediate format.

  Note that the intermediate format is designed to be something that can be easily generated from
  Anltr (on the JVM) or with some other parsing library (in-browser, or Node)."
  {:added "0.27.0 ????????"}
  (:require
    [clj-antlr.core :as antlr.core]
    [clojure.java.io :as io]
    #_[io.pedestal.log :as log]
    #_[clojure.pprint :as pprint]
    [com.walmartlabs.lacinia.parser.common :refer [antlr-parse parse-failures]])
  (:import (clj_antlr ParseError)))

(def ^:private grammar
  (antlr.core/parser (slurp (io/resource "com/walmartlabs/lacinia/Graphql.g4"))))

(defn ^:private as-map
  [prod]
  (->> prod
       rest
       (reduce (fn [m sub-prod]
                 (assoc! m (first sub-prod) (rest sub-prod)))
               (transient {}))
       persistent!))

(defn ^:private copy-meta
  [from to]
  (with-meta to (meta from)))

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
         op-name :name} (as-map prod)
        type (if operationType
               (-> operationType first xform)
               :query)]
    (cond-> (copy-meta prod {:type type})

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
  (xform (-> prod second )))

(defmethod xform :field
  [prod]
  (let [{:keys [name selectionSet alias arguments directives]} (as-map prod)]
    (cond->
      (copy-meta prod
                 {:type :field
                  :field-name (xform (first name))})

      alias (assoc :alias (xform (first alias)))

      selectionSet (assoc :selections (mapv xform selectionSet))

      directives (assoc :directives (mapv xform directives))

      arguments (assoc :args (mapv xform arguments)))))

(defmethod xform :nameid                                    ; Possibly not needed
  [prod]
  (-> prod second keyword))

(defmethod xform :name
  [prod]
  (case (-> prod second first)
    :nameid (-> prod second xform)

    :operationType (-> prod second second xform)))

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
   :value (let [quoted (second prod)]
            (subs quoted 1 (-> quoted .length dec)))})

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
  (let [{:keys [typeCondition directives selectionSet]} (as-map prod)]
    (cond-> {:type :inline-fragment
             :on-type (-> typeCondition first xform)
             :selections (mapv xform selectionSet)}
      directives
      (assoc :directives (mapv xform directives)))))

(defmethod xform :fragmentSpread
  [prod]
  (let [[_ fragment-name directives] prod]
    (cond-> {:type :named-fragment
             :fragment-name (-> fragment-name second xform)}
      directives (assoc :directives (mapv xform (rest directives))))))

(defmethod xform :fragmentDefinition
  [prod]
  (let [[_ fragment-name type-condition selection-set] prod]
    {:type :fragment-definition
     :on-type (-> fragment-name second xform)
     :selections (->> selection-set rest (mapv xform))}))

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
      (antlr-parse grammar input)
      (catch ParseError e
        (let [failures (parse-failures e)]
          (throw (ex-info "Failed to parse GraphQL query."
                          {:errors failures})))))))

