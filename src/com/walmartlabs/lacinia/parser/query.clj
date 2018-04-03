(ns com.walmartlabs.lacinia.parser.query
  "Uses the Antlr grammar to parse a document into the intermediate format.

  Note that the intermediate format is designed to be something that can be easily generated from
  Anltr (on the JVM) or with some other parsing library (in-browser, or Node)."
  {:added "0.27.0 ????????"}
  (:require
    [clj-antlr.core :as antlr.core]
    [clojure.java.io :as io]
    [io.pedestal.log :as log]
    [com.walmartlabs.lacinia.parser.common :refer [antlr-parse]]
    [clojure.pprint :as pprint]))

(def ^:private grammar
  (antlr.core/parser (slurp (io/resource "com/walmartlabs/lacinia/Graphql.g4"))))

(defn ^:private as-map
  [prod]
  (->> prod
       rest
       (reduce (fn [m sub-prod]
                 (assoc! m (first sub-prod) (rest sub-prod)))
               (transient {}))
       persistent!
       #_((fn [x]
            (log/trace :as-map x)
            x))))

(defmulti ^:private xform
  "Transform an Antlr production into a result.

  Antlr productions are recursive lists; the first element is a type
  (from the grammar), and the rest of the list are nested productions."
  (fn [prod]
    (log/trace :dispatch prod)
    (first prod))
  :default ::default)

(defn ^:private arguments-map
  [argument-prods]
  (->> argument-prods
       (mapv as-map)
       (reduce (fn [m arg]
                 (assoc! m
                         (-> arg :name first xform)
                         (-> arg :value first xform)))
               (transient {}))
       persistent!))

(defmethod xform :definition
  [prod]
  (xform (second prod)))

(defmethod xform :operationDefinition
  [prod]
  (let [{:keys [operationType selectionSet variableDefinitions]} (as-map prod)
        type (if operationType
               (xform (first operationType))
               :query)]
    (cond-> {:type type}
      selectionSet (assoc :selections (mapv xform selectionSet))
      variableDefinitions (assoc :vars (->> variableDefinitions
                                            (map rest)      ; strip :variableDefinition
                                            (reduce (fn [m v]
                                                      (log/debug :variable v)
                                                      (assoc! m
                                                              (-> v first second xform)
                                                              (-> v second xform)))
                                                    (transient {}))
                                            persistent!)))))

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

(defmethod xform :selectionSet
  [prod]
  {:type :selection-set
   :selections (mapv xform (rest prod))})

(defmethod xform :selection
  [prod]
  (xform (-> prod second )))

(defmethod xform :field
  [prod]
  (let [{:keys [name selectionSet alias arguments]} (as-map prod)]
    (cond->
      {:type :field
       :field-name (xform (first name))}

      alias (assoc :alias (xform (first alias)))

      selectionSet (assoc :selections (mapv xform selectionSet))

      arguments (assoc :args (arguments-map arguments)))))

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
   :value (-> prod second (= "true"))})

(defmethod xform :intvalue
  [prod]
  {:type :integer
   :value (-> prod second Integer/parseInt)})               ; TODO: Integer or Long?

(defmethod xform :floatvalue
  [prod]
  {:type :float
   :value (-> prod second Double/parseDouble)})             ; TODO: Float or Double?

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
               (map rest)                                   ; drop :objectField
               (reduce (fn [m v]
                         (assoc! m (-> v first xform) (-> v second second xform)))
                       (transient {}))
               persistent!)})

(defmethod xform :variable
  [prod]
  {:type :variable
   :value (-> prod second xform)})

(defn ^:private xform-query
  [antlr-tree]
  (println "Parsed Antlr Tree:")
  (pprint/write antlr-tree)
  (println)
  (let [top-levels (rest antlr-tree)]
    (mapv xform top-levels)))


(defn parse-query
  "Parses an input document (a string) into the intermediate query structure."
  [input]
  (xform-query (antlr-parse grammar input)))

