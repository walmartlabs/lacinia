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

(ns com.walmartlabs.lacinia.internal-utils
  "Internal utilities used in the implementation, subject to change without notice."
  {:no-doc true}
  (:require
    [clojure.string :as str]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [flatland.ordered.map :refer [ordered-map]]
    [clojure.pprint :refer [pprint]])
  (:import
    (clojure.lang Named)
    (com.walmartlabs.lacinia.resolve ResolverResultImpl)))

(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [simple-keyword? boolean?]]))

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
  (let [n (when (instance? Named v)
            (namespace v))]
    (str \`
         n
         (when n "/")
         (name v) \')))

(defn sequential-or-set?
  "Returns true if x is a Sequential or Set"
  [x]
  (or (sequential? x) (set? x)))

(defn is-internal-type-name?
  "Identifies type names that are added by introspection, or a namespaced key used by Lacinia."
  [type-name]
  (or (namespace type-name)
      (str/starts-with? (name type-name) "__")))

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
                   (merge {} *exception-context* (ex-data cause) data)
                   cause))))

(defmacro with-exception-context
  "Merges the provided context into the exception context for the duration of the body."
  [context & body]
  `(binding [*exception-context* (merge *exception-context* ~context)]
     ~@body))

(defn as-keyword
  [v]
  (cond
    (keyword? v) v

    (symbol? v) (-> v name keyword)

    (string? v) (keyword v)

    :else
    (throw (ex-info "Can't convert value to keyword." {:value v}))))

;; NOTE: Parked these things here since namespace reloading in Cursive
;; can get jammed on new class definitions, causing instance? to fail.

(deftype TaggedValue [value tag])

(defn is-tagged-value?
  "Returns true if the value is a tagged value (combining an underlying value with a type tag)."
  {:added "0.17.0"}
  [v]
  (instance? TaggedValue v))

(defn extract-value
  {:added "0.17.0"}
  [^TaggedValue v]
  (.value v))

(defn extract-type-tag
  {:added "0.17.0"}
  [^TaggedValue v]
  (.tag v))

(def ^:private operation-containers #{:queries :mutations :subscriptions})

(defn name->path
  "Given a key, such as :queries/user_by_id or :User/full_name, return the
  path from the root of the schema to (and including) the path-ex key."
  [schema k path-ex]
  (let [container-name (-> k namespace keyword)
        field-name (-> k name keyword)
        path (if (operation-containers container-name)
               [container-name field-name]
               [:objects container-name :fields field-name])]
    (when-not (get-in schema path)
      (throw (ex-info "inject error: not found"
                      {:key k})))

    (conj path path-ex)))

(defn ^:private type-root
  "Looks up the given type keyword in schema and returns the corresponding root key into
  the schema."
  [schema type-name]
  (if (operation-containers type-name)
    type-name
    (let [f (fn [k]
              (when (-> schema (get k) (contains? type-name))
                k))]
      (or
        (some f [:objects :input-objects :interfaces :enums :unions :scalars])
        (throw (ex-info "Error attaching documentation: type not found" {:type-name type-name}))))))

(defn ^:private index-of
  "Returns index of first value in collection that satifies the predicate, or nil if not found."
  [pred coll]
  (->> coll
       (keep-indexed #(when (pred %2)
                        %1))
       first))

(defn ^:private enum-matcher
  [^String enum-value]
  (fn [enum-def]
    (if (map? enum-def)
      (= enum-value (-> enum-def :enum-value name))
      (= enum-value (name enum-def)))))

(defn ^:private apply-enum-description
  [enum-def description]
  (if (map? enum-def)
    (assoc enum-def :description description)
    {:enum-value enum-def
     :description description}))

(defn assoc-in!
  "A variant of `assoc-in` that requires that each k in ks already exist (except the last), throwing an exception if not."
  [m [k & more-ks] v]
  (cond

    (not (seq more-ks))
    (assoc m k v)

    (not (contains? m k))
    (throw (ex-info "Intermediate key not found during assoc-in!"
                    {:map m
                     :key k
                     :more-keys more-ks
                     :value v}))

    :else
    (update m k assoc-in! more-ks v)))

(defn ^:private update-in!*
  [m [k & more-ks] f]
  (cond
    (not (contains? m k))
    (throw (ex-info "Intermdiate key not found during update-in!"
                    {:map m
                     :key k
                     :more-keys more-ks}))

    (not (seq more-ks))
    (assoc m k (f (get m k)))

    :else
    (assoc m k
           (update-in!* (get m k) more-ks f))))

(defn update-in!
  "A variant of `update-in` that requires that each k in ks already exist, throwing an exception if not."
  [m ks f & args]
  ;; Could be optimized when no args
  (update-in!* m ks #(apply f % args)))

(defn apply-description
  "Adds a description to an element of the schema.

  `location` should be one of:
   - `:type`
   - `:type/field`
   - `:type/field.arg`

   The type may identify an object, input object, interface, enum, scalar, or union.

   union's do not have fields, an exception is thrown if a field of an enum is documented.

   enum's have values, not fields.
   It is allowed to document individual enum values, but enum values do not have arguments
   (an exception will be thrown).

   Scalars do not contain anything.

   Returns an updated schema."
  [schema location description]
  (cond-let
    :let [simple? (simple-keyword? location)
          type-name (if simple?
                      location
                      (-> location namespace keyword))
          [field-name arg-name] (when-not simple?
                                  (-> location name (str/split #"\." 2)))
          root (type-root schema type-name)]

    simple?
    (assoc-in! schema [root type-name :description] description)

    (= :unions root)
    (throw (ex-info "Error attaching documentation: union members may not be documented"
                    {:type-name type-name}))

    (= :scalars root)
    (throw (ex-info "Error attaching documentation: scalars do not contain fields"
                    {:type-name type-name}))

    :let [field-name' (keyword field-name)]

    (= :enums root)
    (if-not (str/blank? arg-name)
      (throw (ex-info "Error attaching documentation: enum values do not contain fields"
                      {:type-name type-name}))
      ;; The field-name is actually the enum value, in this context
      (if-let [ix (index-of (enum-matcher field-name)
                            (get-in schema [root type-name :values]))]
        (update-in! schema [root type-name :values ix] apply-enum-description description)
        (throw (ex-info "Error attaching documentation: enum value not found"
                        {:type-name type-name
                         :enum-value field-name'}))))

    :let [base (if (operation-containers root)
                 [root field-name']
                 [root type-name :fields field-name'])]

    (str/blank? arg-name)
    (assoc-in! schema (conj base :description) description)

    :else
    (assoc-in! schema (conj base :args (keyword arg-name) :description) description)))

(defn qualified-name
  "Builds a keyword that is the qualified name, e.g. :MyObject/myfield or :MyObject/myfield.myarg.

  For directives, there is no namespace, so :MyDirective.myarg."
  ([type-name field-name]
   (if (some? type-name)
     (keyword (name type-name)
              (name field-name))
     (keyword field-name)))
  ([type-name field-name arg-name]
   (qualified-name type-name (str (name field-name) "." (name arg-name)))))

(defn aggregate-results
  "Combines a seq of ResolverResults into a single ResolverResult(Promise) that resolves
   to a seq of values.

   An optional transform function, fx, is passed the resolved seq of values.
   The default xf is identity."
  ([resolver-results]
   (aggregate-results resolver-results identity))
  ([resolver-results xf]
   (cond-let
     :let [results (vec resolver-results)
           n (count results)]

     (= 0 n)
     (resolve/resolve-as (xf []))

     :let [solo (when (= 1 n)
                  (first results))]

     (and solo
          (instance? ResolverResultImpl solo))
     (resolve/resolve-as (xf [(:resolved-value solo)]))

     :let [aggregate (resolve/resolve-promise)]

     solo
     (do
       (resolve/on-deliver! solo (fn [value]
                                   (resolve/deliver! aggregate (xf [value]))))
       aggregate)

     :let [buffer (object-array n)
           *wait-count (atom 1)
           _ (loop [i 0]
               (when (< i n)
                 (let [result (get results i)]
                   (if (instance? ResolverResultImpl result)
                     (aset buffer i (:resolved-value result))
                     (do
                       (swap! *wait-count inc)
                       (resolve/on-deliver! result
                                            (fn [value]
                                              (aset buffer i value)
                                              (when (zero? (swap! *wait-count dec))
                                                (resolve/deliver! aggregate (-> buffer vec xf))))))))
                 (recur (inc i))))]

     ;; Started count at 1, if it dec's to 0 now, that means all the
     ;; ResolverResults were immediate (not promises)
     (zero? (swap! *wait-count dec))
     (resolve/resolve-as (-> buffer vec xf))

     :else
     aggregate)))

(defn transform-result
  "Passes the resolved value of an existing ResolverResult through a transforming
   function, resulting in a new ResolverResult.

   Optimizes the case for a ResolverResult (pre-realized) vs.
   a ResolverResultPromise."
  [resolver-result xf]
  (if (instance? ResolverResultImpl resolver-result)
    (resolve/resolve-as (-> resolver-result :resolved-value xf))
    (let [xformed (resolve/resolve-promise)]
      (resolve/on-deliver! resolver-result
                           (fn [value]
                             (resolve/deliver! xformed (xf value))))
      xformed)))

(defn seek
  "Returns the first value of coll for which pred returns a truthy value."
  [pred coll]
  (reduce (fn [_ v]
            (when (pred v)
              (reduced v)))
          nil
          coll))

(def ^:dynamic *compile-trace* false)

(def ^:dynamic *enable-trace* true)

(defn set-compile-trace!
  [value]
  (alter-var-root #'*compile-trace* (constantly value)))

(defn set-enable-trace!
  [value]
  (alter-var-root #'*enable-trace* (constantly value)))

(defmacro trace
  [& kvs]
  (assert (even? (count kvs))
          "pass key/value pairs")
  (when *compile-trace*
    (let [{:keys [line]} (meta &form)
          trace-ns *ns*]
      `(when *enable-trace*
         ;; Oddly, you need to defer invocation of ns-name to execution
         ;; time as it will cause odd class loader exceptions if used at
         ;; macro expansion time.
         (pprint (array-map
                   :ns (ns-name ~trace-ns)
                   ~@(when line [:line line])
                   ~@kvs))))))

(defn ^:private ordered-group-by
  "Copy of clojure.core/ordered-by that uses an ordered map."
  [f coll]
  (reduce
    (fn [ret x]
      (let [k (f x)]
        (assoc ret k (conj (get ret k []) x))))
    (ordered-map)
    coll))

(defn ^:private assoc*
  [coll k v empty-map]
  (let [coll* (cond
                ;; Because inside a reducer function, it's likely coll has already been
                ;; created.
                (some? coll)
                coll

                ;; Otherwise, need to create a transient collection based on the kind of
                ;; key (integer implies a vector, otherwise an empty map).

                (integer? k)
                (transient [])

                :else
                (transient empty-map))]
    (assoc! coll* k v)))

(defn ^:private split-on
  [f coll]
  (let [*matches (volatile! nil)
        *others (volatile! nil)]
    (doseq [v coll]
      (let [*x (if (f v) *matches *others)]
        (vswap! *x #(conj (or %1 []) %2) v)))
    [@*matches @*others]))

(defn ^:private assemble
  [kx terms empty-map]
  (let [by-first (ordered-group-by #(nth % kx) terms)
        kx+1 (inc kx)
        kx+2 (inc kx+1)
        reducer-fn (fn [coll* [k k-terms]]
                     (let [[leaf-terms nested-terms] (split-on #(= (count %) kx+2) k-terms)
                           ;; Apply the (usually, at most 1) leaf terms first:
                           coll1 (if (some? leaf-terms)
                                   (reduce (fn [coll term]
                                             (assoc* coll k (nth term kx+1) empty-map))
                                           coll*
                                           leaf-terms)
                                   coll*)
                           coll2 (cond-> coll1
                                   (some? nested-terms) (assoc* k (assemble kx+1 nested-terms empty-map) empty-map))]
                       coll2))]
    (persistent! (reduce reducer-fn nil by-first))))

(defn assemble-collection
  "Assembles a seq of key/value paths into a nested structure (maps and vectors).

  Each term is a vector; the initial values form the key path, the final value is the value at that keypath.

  Numeric keys imply a vector rather than a map.

  The two arity version provides a default empty map (it must be a map, and empty); this allows
  for using alternative map implementations; specifically the flatland OrderedMap.

  Usage:

  (assemble-collection [[:root :user :firstName \"Howard\"]
                        [:root :user :empNo 876321]
                        [:root :lastUpdated \"2021-04-14\"]])
    "
  ([terms]
   (assemble-collection terms {}))
  ([terms empty-map]
   (assert (map? empty-map))
   (assert (empty? empty-map))
   (assemble 0 terms empty-map)))
