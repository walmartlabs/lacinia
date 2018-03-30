(ns ^:no-doc com.walmartlabs.lacinia.constants
  "A handy place to define namespaced constants")

(def schema-key
  "Context key storing the compiled schema."
  ::schema)

(def parsed-query-key
  "Context key storing the parsed and prepared query."
  ::parsed-query)

(def ^{:added "0.17.0"} selection-key
  "Context key storing the current selection."
  :com.walmartlabs.lacinia/selection)
