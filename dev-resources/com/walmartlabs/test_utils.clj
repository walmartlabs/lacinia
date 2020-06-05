(ns com.walmartlabs.test-utils
  (:require
    [clojure.test :refer [is]]
    [clojure.walk :as walk]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia :as lacinia]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.schema :as schema])
  (:import
    (clojure.lang IPersistentMap)))

(defmacro is-thrown
  "Expects the expression to thrown an exception.

  If the expression does throw, then the exception
  is caught, bound to the symbol, and the body is evaluated.

  The body can then perform tests on the details of the exceptions.

  Otherwise, if no exception is thrown, then a new exception *is* thrown,
  detailing the expression that was expected to fail.

  sym
  : symbol that we be bound to the exception thrown from the expression

  exp
  : expression to evaluate

  body
  : evaluated with sym bound

  Example:

      (is-thrown [e (parse-invalid-file \"invalid.txt\")]
        (is (= :parse/failure
            (-> e ex-data :type))))

  Evaulates to nil."
  [[sym exp] & body]
  `(let [~sym (is (~'thrown? Throwable ~exp))]
     (when (instance? Throwable ~sym)
       ~@body)))

(defn simplify
  "Converts all ordered maps nested within the map into standard hash maps, and
   sequences into vectors, which makes for easier constants in the tests, and eliminates ordering problems."
  [m]
  (walk/postwalk
    (fn [node]
      (cond
        (instance? IPersistentMap node)
        (into {} node)

        (seq? node)
        (vec node)

        :else
        node))
    m))

(defn compile-schema
  "Reads a schema EDN file, attaches resolvers, and compiles the schema."
  ([resource-path resolvers]
   (compile-schema resource-path resolvers {}))
  ([resource-path resolvers options]
   (-> (io/resource resource-path)
       slurp
       edn/read-string
       (util/attach-resolvers resolvers)
       (schema/compile options))))

(defn execute
  ([compiled-schema query]
    (execute compiled-schema query nil nil))
  ([compiled-schema query vars context]
   (execute compiled-schema query vars context nil))
  ([compiled-schema query vars context options]
   (simplify (lacinia/execute compiled-schema query vars context options))))

(defmacro expect-exception
  [expected-message expected-data form]
  `(when-let [e# (is (~'thrown? Throwable ~form))]
     (is (= ~expected-message (ex-message e#)))
     (is (= ~expected-data (ex-data e#)))))
