(ns com.walmartlabs.lacinia.parser.query
  "Uses the Antlr grammar to parse a document into the intermediate format.

  Note that the intermediate format is designed to be something that can be easily generated from
  Anltr (on the JVM) or with some other parsing library (in-browser, or Node)."
  {:added "0.27.0 ????????"}
  (:require
    [clj-antlr.core :as antlr.core]
    [clojure.java.io :as io]
    [io.pedestal.log :as log]
    [com.walmartlabs.lacinia.parser.common :refer [antlr-parse]]))

(def ^:private grammar
  (antlr.core/parser (slurp (io/resource "com/walmartlabs/lacinia/Graphql.g4"))))

(defmulti ^:private xform
  "Transform an Antlr production into a result."
  (fn [prod]
    (log/trace :event :dispatch :prod prod)
    (first prod))
  :default ::default)

(defmethod xform ::default
  [_]
  [])

(defmethod xform :definition
  [prod]
  (case (-> prod second first)

    :operationDefinition
    (xform (-> prod second second))))

(defmethod xform :selectionSet
  [prod]
  {:type :selection-set
   :selections (mapv xform (rest prod))})

(defmethod xform :selection
  [prod]
  (xform (-> prod second )))

(defmethod xform :field
  [prod]
  {:type :field
   :field-name (xform (second prod))})

(defmethod xform :name
  [prod]
  (xform (second prod)))

(defmethod xform :nameid
  [prod]
  (-> prod second keyword))

(defn ^:private xform-query
  [antlr-tree]
  (let [top-levels (rest antlr-tree)]
    (mapv xform top-levels)))


(defn parse-query
  "Parses an input document (a string) into the intermediate query structure."
  [input]
  (xform-query (antlr-parse grammar input)))

