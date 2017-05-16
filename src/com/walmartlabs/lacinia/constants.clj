(ns ^:no-doc com.walmartlabs.lacinia.constants
  "A handy place to define namespaced constants")

(def query-root
  "Object in the compiled schema that contains, as fields, all queries."
  :QueryRoot)

(def mutation-root
  "Object in the compiled schema that contains, as fields, all mutations."
  :MutationRoot)

(def schema-key
  "Context key storing the compiled schema."
  ::schema)

(def parsed-query-key
  "Context key storing the parsed and prepared query."
  ::parsed-query)

(def ^{:added "0.17.0"} selection-key
  "Context key storing the current selection."
  :com.walmartlabs.lacinia/selection)
