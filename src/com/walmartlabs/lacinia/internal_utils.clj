(ns com.walmartlabs.lacinia.internal-utils
  "Internal utilities used in the implementation, subject to change without notice."
  {:no-doc true}
  (:require [clojure.string :as str]))

(defn ensure-seq
  "Errors may be nil, a single map, or a sequence of maps; this is is used to normalize
  a map to be a vector of a single map."
  [error-or-errors]
  (cond
    (map? error-or-errors)
    [error-or-errors]

    :else
    error-or-errors))

(defn seqv
  "Returns nil if coll is empty, otherwise, returns a vector
  of the contents of the collection."
  [coll]
  (and (seq coll)
       (vec coll)))

(defmacro cond-let
  "A version of `cond` that allows for `:let` terms. There is hope that someday, perhaps
  after Rich Hickey retires, this will make it into clojure.core."
  [& forms]
  {:pre [(even? (count forms))]}
  (when forms
    (let [[test-exp result-exp & more-forms] forms]
      (if (= :let test-exp)
        `(let ~result-exp
           (cond-let ~@more-forms))
        `(if ~test-exp
           ~result-exp
           (cond-let ~@more-forms))))))

(defn keepv
  [f coll]
  (into [] (keep f) coll))

(defn hash-map-by
  "Constructs a hash map from the supplied values.

  key-fn
  : Passed a value and extracts the key for that value.

  values
  : Seq of values from which map keys and values will be extracted."
  [key-fn values]
  (zipmap (map key-fn values) values))

(defn to-message
  "Converts an exception to a message. Normally, this is the message property of the exception, but if
  that's blank, the fully qualified class name of the exception is used instead."
  [^Throwable t]
  (let [m (.getMessage t)]
    (if-not (str/blank? m)
      m
      (-> t .getClass .getName))))

(defn map-vals
  "Builds a new map with values passed through f."
  [f m]
  (when m
    (-> (reduce-kv (fn [m k v]
                     (assoc! m k (f v)))
                   (-> m empty transient)
                   m)
        persistent!
        (with-meta (meta m)))))

(defn map-kvs
  "Builds a new map passing each key and value to f, which
  returns a tuple of new key and new value."
  [f m]
  (when m
    (-> (reduce-kv (fn [m k v]
                     (let [[k' v'] (f k v)]
                       (assoc! m k' v')))
                   (-> m empty transient)
                   m)
        persistent!
        (with-meta (meta m)))))

(defn filter-vals
  [pred m]
  (when m
    (-> (reduce-kv (fn [m k v]
                     (if (pred v)
                       (assoc! m k v)
                       m))
                   (-> m empty transient)
                   m)
        persistent!
        (with-meta (meta m)))))

(defn remove-vals
  [pred m]
  (filter-vals (complement pred) m))

(defn filter-keys
  [pred m]
  (when m
    (-> (reduce-kv (fn [m k v]
                     (if (pred k)
                       (assoc! m k v)
                       m))
                   (-> m empty transient)
                   m)
        persistent!
        (with-meta (meta m)))))

(defn remove-keys
  [pred m]
  (filter-keys (complement pred) m))

(defn deep-merge
  "Like merge, but merges maps recursively."
  [& maps]
  (if (every? (some-fn nil? map?) maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(defn update?
  "If the value inside m at k is non-nil, then it is updated, otherwise,
  when the value is nil, the map is returned unchanged."
  [m k f & args]
  (if (some? (get m k))
    (apply update m k f args)
    m))

(defn q
  "Quotes a keyword, string, or symbol inside back-tick and quote, for use in error messages."
  [v]
  (str \` (name v) \'))

(defn is-internal-type-name?
  "Identifies type names that are added by introspection."
  [type-name]
  (str/starts-with? (name type-name) "__"))

(def ^:dynamic *exception-context*
  "Map of extra values to be added to the exception data when an exception is created."
  nil)

(defn throw-exception
  "Creates an exception and immediately throws it."
  ([message]
   (throw-exception message nil))
  ([message data]
   (throw-exception message data nil))
  ([message data cause]
   (throw (ex-info message
                   (merge *exception-context* (ex-data cause) data)
                   cause))))

(defmacro with-exception-context
  "Merges the provided context into the exception context for the duration of the body."
  [context & body]
  `(binding [*exception-context* (merge *exception-context* ~context)]
     ~@body))
