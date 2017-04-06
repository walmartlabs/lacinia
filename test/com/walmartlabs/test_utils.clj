(ns com.walmartlabs.test-utils
  (:require
    [clojure.test :refer [is]]
    [clojure.spec.test :as stest]
    flatland.ordered.map
    [clojure.walk :as walk])
  (:import
    (flatland.ordered.map OrderedMap)))

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

(defn instrument-schema-namespace
  []
  (-> (stest/enumerate-namespace 'com.walmartlabs.lacinia.schema)
      stest/instrument
      stest/check))

(defn simplify
  "Converts all ordered maps nested within the map into standard hash maps, and
   sequences into vectors, which makes for easier constants in the tests, and eliminates ordering problems."
  [m]
  (walk/postwalk
    (fn [node]
      (cond
        (instance? OrderedMap node)
        (into {} node)

        (seq? node)
        (vec node)

        :else
        node))
    m))
