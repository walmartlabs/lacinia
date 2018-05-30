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
    [clojure.string :as str])
  (:import (clojure.lang Named)))

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

(defn resolver-path
  "Given a key, such as :queries/user_by_id or :User/full_name, return the
  path from the root of the schema to (and including) the :resolve key."
  [schema k]
  (let [container-name (-> k namespace keyword)
        field-name (-> k name keyword)
        path (if (operation-containers container-name)
               [container-name field-name]
               [:objects container-name :fields field-name])]
    (when-not (get-in schema path)
      (throw (ex-info "inject resolvers error: not found"
                      {:key k})))

    (conj path :resolve)))

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
        (some f [:objects :input-objects :interfaces :enums :unions])
        (throw (ex-info "Error attaching documentation: type not found" {:type-name type-name}))))))

(defn ^:private index-of
  "Index of a value in a vector, or nil if not found."
  [v value]
  (->> v
       (keep-indexed #(when (= %2 value)
                        %1))
       first))

(defn documentation-schema-path
  "Returns a sequence of keys representing the path where the
  documentation string should be attached in the nested associative schema
  structure.

  `location` should be one of:
   - `:type`
   - `:type/field`
   - `:type/field.arg`

   The type may identify an object, input object, interface, enum, or union.

   union's do not have fields, an exception is thrown if a field of an enum is documented.

   enum's have values, not fields.
   It is allowed to document individual enum values, but enum values do not have arguments
   (an exception will be thrown)."
  [schema location]
  (cond-let
    :let [simple? (simple-keyword? location)
          type-name (if simple?
                      location
                      (-> location namespace keyword))
          [field-name arg-name] (when-not simple?
                                  (-> location name (str/split #"\." 2)))
          root (type-root schema type-name)]

    simple?
    [root type-name :description]

    (= :unions root)
    (throw (ex-info "Error attaching documentation: union members may not be documented"
                    {:type-name type-name}))

    :let [field-name' (keyword field-name)]

    (= :enums root)
    (if-not (str/blank? arg-name)
      (throw (ex-info "Error attaching documentation: enum values do not have fields"
                      {:type-name type-name}))
      ;; The field-name is actually the enum value, in this context
      (if-let [ix (index-of (get-in schema [root type-name :values])
                            {:enum-value field-name'})]
        [root type-name :values ix :description]
        (throw (ex-info "Error attaching documentation: enum value not found"
                        {:type-name type-name
                         :enum-value field-name'}))))

    :let [base (if (operation-containers root)
                 [root field-name']
                 [root type-name :fields field-name'])]

    (str/blank? arg-name)
    (conj base :description)

    :else
    (conj base :args (keyword arg-name) :description)))

