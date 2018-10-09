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

(ns com.walmartlabs.lacinia.schema
  "Responsible for constructing and validating the GraphQL schema.

  GraphQL schema starts in a format easy to read and maintain as an EDN file.

  Compiling the schema performs a number of validations and reorganizations to
  make query execution faster and simpler, such as generating a flatter structure for the
  schema, and pre-computing many defaults."
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.compiled-schema :refer [map->CompiledSchema]]
    [com.walmartlabs.lacinia.introspection :as introspection]
    [com.walmartlabs.lacinia.internal-utils
     :refer [map-vals map-kvs filter-vals deep-merge q
             is-internal-type-name? sequential-or-set? as-keyword
             cond-let ->TaggedValue is-tagged-value? extract-value extract-type-tag]]
    [com.walmartlabs.lacinia.resolve :as resolve :refer [ResolverResult resolve-as combine-results is-resolver-result?]]
    [clojure.string :as str]
    [clojure.set :refer [difference]]
    [clojure.pprint :as pprint])
  (:import
    (clojure.lang IObj)
    (java.io Writer)
    (com.walmartlabs.lacinia.resolve ResolveCommand)
    (com.walmartlabs.lacinia.compiled_schema CompiledSchema)))

;; When using Clojure 1.8, the dependency on clojure-future-spec must be included,
;; and this code will trigger
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [any? simple-keyword? simple-symbol?]]))

;;-------------------------------------------------------------------------------
;; ## Helpers

(def ^:private graphql-identifier #"(?i)_*[a-z][a-zA-Z0-9_]*")

(defrecord ^:private CoercionFailure
  [message])

(defn coercion-failure
  "Returns a special record that indicates a failure coercing a scalar value.
  This may be returned from a scalar's :parse or :serialize callback.

  A coercion failure includes a message key, and may also include additional data.

  message
  : A message string presentable to a user.

  data
  : An optional map of additional details about the failure."
  {:added "0.16.0"}
  ([message]
   (coercion-failure message nil))
  ([message data]
   (merge (->CoercionFailure message) data)))

(defn is-coercion-failure?
  "Is this a coercion error created by [[coercion-failure]]?"
  {:added "0.16.0"}
  [v]
  (instance? CoercionFailure v))

(defn ^:private map-types
  "Maps the types of the schema that match the provided category, but leaves
   the rest unchanged."
  [schema category f]
  (reduce-kv (fn [s k v]
               (if (-> v :category (= category))
                 (assoc s k (f v))
                 s))
             schema
             schema))

(defn tag-with-type
  "Tags a value with a GraphQL type name, a keyword.
  The keyword should identify a specific concrete object
  (not an interface or union) in the relevent schema.

  In most cases, this is accomplished by modifying the value's metadata.

  For the more rare case, where a particular type is used rather than a Clojure
  map, this function returns a new wrapper instance that combines the value and the type name."
  [x type-name]
  (cond
    ;; IObj is the base interface for things that can vary their metadata:
    (instance? IObj x)
    (vary-meta x assoc ::type-name type-name)

    ;; From here on is the edge case where a fixed type is used that doesn't
    ;; support metadata.

    ;; In some cases, the resolver for a field may tag a value even though it
    ;; gets re-tagged automatically.

    (is-tagged-value? x)
    (if (= type-name (extract-type-tag x))
      x
      (->TaggedValue (extract-value x) type-name))

    :else
    (->TaggedValue x type-name)))

(defn ^:no-doc type-map
  "Reduces a compiled schema to a list of categories and the names of types in that category, useful
  for exception reporting."
  [schema]
  (->> schema
       vals
       (filter :type-name)                                    ;; ::schema/root has no :type-name
       (group-by :category)
       (map-vals #(->> (map :type-name %)
                       (remove is-internal-type-name?)
                       sort
                       vec))
       ;; The above may remove entire categories, drop the keys when
       ;; all the values have been filtered out.
       (filter-vals seq)))

(defn as-conformer
  "Creates a clojure.spec/conformer as a wrapper around the supplied function.

  The function is only invoked if the value to be conformed is non-nil.

  Any exception thrown by the function is silently caught and the returned conformer
  will return :clojure.spec/invalid or a [[coercion-failure]]."
  [f]
  (s/conformer
    (fn [x]
      (try
        (when (some? x)
          (f x))
        (catch Exception e
          (if-some [message (.getMessage e)]
            (coercion-failure message (ex-data e))
            ::s/invalid))))))

(defn ^:private invalid-scalar
  [type-name value]
  (ex-info (format "Invalid %s value." (name type-name))
           {:value (pr-str value)}))

(defmacro ^:private catch-as-invalid
  [& body]
  `(try
     ~@body
     (catch Throwable _#
       ::s/invalid)))

(defn ^:private parse-int
  [v]
  (cond
    (integer? v)
    (if (<= Integer/MIN_VALUE v Integer/MAX_VALUE)
      (int v)
      (throw (ex-info "Int value outside of allowed 32 bit integer range." {:value v})))

    (string? v) (catch-as-invalid (Integer/parseInt v))

    :else (throw (invalid-scalar :Int v))))

(defn ^:private serialize-int
  [v]
  (cond
    ;; Spec: should attempt to coerce raw values to int
    (string? v)
    (let [v' (catch-as-invalid (Integer/parseInt v))]
      (if (keyword? v')
        v'
        (recur v')))

    (and (number? v)
         (<= Integer/MIN_VALUE v Integer/MAX_VALUE))
    (int v)

    :else
    (throw (ex-info "Int value outside of allowed 32 bit integer range." {:value (pr-str v)}))))

(defn ^:private coerce-to-float
  [v]
  (cond
    (instance? Double v)
    v

    ;; Spec: should coerce non-floating-point raw values as result coercion and for input coercion
    (and (number? v))
    (double v)

    (string? v)
    (catch-as-invalid (Double/parseDouble v))

    :else
    (throw (invalid-scalar :Float v))))

(defn ^:private string->boolean
  [^String s]
  (case s
    "true" true
    "false" false
    (throw (ex-info "Boolean string must be `true' or `false'." {:value s}))))

(defn ^:private parse-boolean
  [v]
  (cond
    (instance? Boolean v)
    v

    (string? v)
    (string->boolean v)

    :else
    (throw (invalid-scalar :Boolean v))))

(defn ^:private serialize-boolean
  [v]
  (cond
    (instance? Boolean v) v

    ;; Spec: coerce non-boolean raw values to Boolean when possible.
    (number? v)
    (not (zero? v))

    (string? v) (string->boolean v)

    :else
    (throw (invalid-scalar :Boolean v))))

(def default-scalar-transformers
  (let [str-conformer (as-conformer str)
        float-conformer (as-conformer coerce-to-float)]
    {:String {:parse str-conformer
              :serialize str-conformer}
     :Float {:parse float-conformer
             :serialize float-conformer}
     :Int {:parse (as-conformer parse-int)
           :serialize (as-conformer serialize-int)}
     :Boolean {:parse (as-conformer parse-boolean)
               :serialize (as-conformer serialize-boolean)}
     :ID {:parse str-conformer
          :serialize str-conformer}}))

(defn ^:private error
  ([message]
   (error message nil))
  ([message data]
   (merge {:message message} data)))

;;-------------------------------------------------------------------------------
;; ## Validations

(s/def ::deprecated (s/or :basic true?
                          :detailed string?))
(s/def ::schema-key (s/and simple-keyword?
                           ::graphql-identifier))
(s/def ::graphql-identifier #(re-matches graphql-identifier (name %)))
;; For style and/or historical reasons, type names can be a keyword or a symbol.
;; The convention is that built-in types used a symbol, and application-defined types
;; use a keyword.
(s/def ::type-name (s/and
                     (s/nonconforming
                       (s/or :keyword simple-keyword?
                             :symbol simple-symbol?))
                     ::graphql-identifier))
(s/def ::type (s/or :base-type ::type-name
                    :wrapped-type ::wrapped-type))
(s/def ::wrapped-type (s/cat :modifier ::wrapped-type-modifier
                             :type ::type))
;; Use of a function here, rather than just the set, is due to https://github.com/bhb/expound/issues/101
(s/def ::wrapped-type-modifier #(contains? #{'list 'non-null} %))
(s/def ::arg (s/keys :req-un [::type]
                     :opt-un [::description
                              ::default-value]))
(s/def ::default-value any?)
(s/def ::args (s/map-of ::schema-key ::arg))
;; Defining these callbacks in spec has been a challenge. At some point,
;; we can expand this to capture a bit more about what a field resolver
;; is passed and should return.
(s/def ::resolve (s/or :function ::resolver-fn
                       :protocol ::resolver-type))
(s/def ::resolver-fn ifn?)
(s/def ::resolver-type #(satisfies? resolve/FieldResolver %))
(s/def ::field (s/keys :opt-un [::description
                                ::resolve
                                ::args
                                ::deprecated]
                       :req-un [::type]))
(s/def ::operation (s/keys :opt-un [::description
                                    ::deprecated
                                    ::args]
                           :req-un [::type
                                    ::resolve]))
(s/def ::fields (s/map-of ::schema-key ::field))
(s/def ::implements (s/and (s/coll-of ::type-name)
                           seq))
(s/def ::description string?)
(s/def ::directives (s/coll-of :directive))
(s/def ::directive (s/keys :req-un [::directive-type]
                           :opt-un [::directive-args]))
(s/def ::directive-type ::schema-key)
(s/def ::directive-args (s/map-of keyword? any?))
(s/def ::tag (s/or
               :symbol symbol?
               :class class?))
(s/def ::object (s/keys :req-un [::fields]
                        :opt-un [::implements
                                 ::description
                                 ::tag]))
;; Here we'd prefer a version of ::fields where :resolve was not defined.
(s/def ::interface (s/keys :opt-un [::description
                                    ::fields]))
;; A list of keyword identifying objects that are part of a union.
(s/def ::members (s/and (s/coll-of ::type-name)
                        seq))
(s/def ::union (s/keys :opt-un [::description]
                       :req-un [::members]))
(s/def ::enum-value (s/and (s/nonconforming
                             (s/or :string string?
                                   :keyword simple-keyword?
                                   :symbol simple-symbol?))
                           ::graphql-identifier))
(s/def ::enum-value-def (s/or :bare-value ::enum-value
                              :described (s/keys :req-un [::enum-value]
                                                 :opt-un [::description
                                                          ::deprecated])))
(s/def ::values (s/and (s/coll-of ::enum-value-def) seq))
(s/def ::enum (s/keys :opt-un [::description]
                      :req-un [::values]))
;; The type of an input object field is more constrained than an ordinary field, but that is
;; handled with compile-time checks.  Input objects should not have a :resolve or :args as well.
;; Defining an input-object in terms of :properties (with a corresponding ::properties and ::property spec)
;; may be more correct, but it's a big change.
(s/def ::input-object (s/keys :opt-un [::description]
                              :req-un [::fields]))
(s/def ::parse s/spec?)
(s/def ::serialize s/spec?)
(s/def ::scalar (s/keys :opt-un [::description]
                        :req-un [::parse
                                 ::serialize]))
(s/def ::scalars (s/map-of ::schema-key ::scalar))
(s/def ::interfaces (s/map-of ::schema-key ::interface))
(s/def ::objects (s/map-of ::schema-key ::object))
(s/def ::input-objects (s/map-of ::schema-key ::input-object))
(s/def ::enums (s/map-of ::schema-key ::enum))
(s/def ::unions (s/map-of ::schema-key ::union))

(s/def ::context (s/nilable map?))

;; These are the argument values passed to a resolver or streamer;
;; as opposed to ::args which are argument definitions.
(s/def ::arguments (s/nilable (s/map-of ::schema-key any?)))

;; Same issue as with ::resolve.
(s/def ::stream ifn?)

(s/def ::queries (s/map-of ::schema-key ::operation))
(s/def ::mutations (s/map-of ::schema-key ::operation))

(s/def ::subscription (s/keys :opt-un [::description
                                       ::resolve
                                       ::args]
                              :req-un [::type
                                       ::stream]))

(s/def ::subscriptions (s/map-of ::schema-key ::subscription))

(s/def ::directive-defs (s/map-of ::schema-key ::directive-def))
(s/def ::directive-def (s/keys :opt-un [::description
                                        ::args]
                               :req-un [::locations]))

(s/def ::locations (s/coll-of ::location))
(s/def ::location #{:query :mutation :subscription
                    :field :fragment-definition :fragment-spread :inline-fragment
                    :schema :scalar :object
                    :field-definition :argument-definition :interface
                    :union :enum :enum-value :input-object :input-field-definition})

(s/def ::roots (s/map-of #{:query :mutation :subscription} ::schema-key))

(s/def ::schema-object
  (s/keys :opt-un [::scalars
                   ::interfaces
                   ::objects
                   ::input-objects
                   ::enums
                   ::unions
                   ::roots
                   ::queries
                   ::mutations
                   ::subscriptions
                   ::directive-defs]))

;; Again, this can be fleshed out once we have a handle on defining specs for
;; functions:
(s/def ::default-field-resolver ifn?)

(s/def ::compile-options (s/keys :opt-un [::default-field-resolver]))

(defmulti ^:private check-compatible
  "Given two type definitions, dispatches on a vector of the category of the two types.
  'Returns true if the two types are compatible.

  The interface defines the constraint type, the field defines the constrained type.

  This is only invoked when the constraint type and constrained types are not equal.

  The rules for this are in section 3.1.2.3 of the spec."
  (fn [constraint-type constrained-type]
    (mapv :category [constraint-type constrained-type])))

(defmethod check-compatible :default
  [_ _]
  ;; Remember that for object-vs-object, scalar-vs-scalar, and
  ;; enum-vs-enum, we don't get this far if the types are the same.
  ;; For disparate types, generally not compatible (e.g., enum vs. scalar).
  false)

(defmethod check-compatible [:union :object]
  [i-type f-type]
  (contains? (:members i-type) (:type-name f-type)))

(defmethod check-compatible [:interface :object]
  [i-type f-type]
  (contains? (:implements f-type) (:type-name i-type)))

;; That's as far as the spec goes, but one could imagine additonal rules
;; such as a union-vs-union (the field union must be a subset of the interface union),
;; or interface-union (all members of the union must implement the interface).

(defn ^:private is-compatible-type?
  "Compares two field type maps (on from the interface, one from the object) for compatibility."
  [schema interface-type object-type]
  (let [i-kind (:kind interface-type)
        o-kind (:kind object-type)
        i-type (:type interface-type)
        o-type (:type object-type)]
    (cond
      ;; When the object field is non-null and the interface field allows nulls that's ok,
      ;; the object can be more specific than the interface.
      (and (= o-kind :non-null)
           (not= i-kind :non-null))
      (recur schema i-kind o-type)

      ;; Otherwise :list must match :list, and :root must match :root,
      ;; and :non-null must match :non-null
      (not= o-kind i-kind)
      false

      ;; For :list and :non-null, they match, move down a level, towards :root
      (#{:list :non-null} o-kind)
      (recur schema i-type o-type)

      ;; Shortcut the compatible type check if the exact same type
      (= i-type o-type)
      true

      :else
      (check-compatible (get schema i-type)
                        (get schema o-type)))))

(defn ^:private is-assignable?
  "Returns true if the object field is type compatible with the interface field."
  [schema interface-field object-field]
  (let [interface-type (:type interface-field)
        object-type (:type object-field)]
    (or (= interface-type object-type)
        (is-compatible-type? schema interface-type object-type))))

;;-------------------------------------------------------------------------------
;; ## Types

(defn ^:private expand-type
  "Compiles a type from the input schema to the format used in the
  compiled schema."
  ;; TODO: This nested maps format works, but given the simple modifiers
  ;; we have, just converting from nested lists to a flattened vector
  ;; might work just as well. It would also make finding the root type
  ;; cheap: just use last.
  [type]
  (cond
    (sequential? type)
    (let [[modifier next-type & anything-else] type
          kind (get {'list :list
                     'non-null :non-null} modifier)]
      (when (or (nil? next-type)
                (nil? kind)
                (seq anything-else))
        (throw (ex-info "Expected (list|non-null <type>)."
                        {:type type})))

      {:kind kind
       :type (expand-type next-type)})

    ;; By convention, symbols are used for pre-defined scalar types, and
    ;; keywords are used for user-defined types, interfaces, unions, enums, etc.
    (or (keyword? type)
        (symbol? type))
    {:kind :root
     :type (as-keyword type)}

    :else
    (throw (ex-info "Could not process type."
                    {:type type}))))

(defn ^:no-doc root-type-name
  "For a compiled field (or argument) definition, delves down through the :type tag to find
  the root type name, a keyword."
  [field-def]
  ;; In some error scenarios, the query references an unknown field and
  ;; the field-def is nil. Without this check, this loops endlessly.
  (when field-def
    (loop [type-def (:type field-def)]
      (if (-> type-def :kind (= :root))
        (:type type-def)
        (recur (:type type-def))))))

(defn ^:private rewrite-type
  "Rewrites the type tag of a field (or argument) into a nested structure of types.

  types are maps with two keys, :kind and :type.

  :kind may be :list, :non-null, or :root.

  :type is a nested type map, or (for :root kind), the keyword name of a
  schema type (a scalar, enum, object, etc.)."
  [field]
  (try
    (update field :type expand-type)
    (catch Throwable t
      (throw (ex-info "Could not identify type of field."
                      {:field field}
                      t)))))

(defn ^:private compile-arg
  "It's convinient to treat fields and arguments the same at this level."
  [arg-name arg-def]
  (-> arg-def
      rewrite-type
      (assoc :arg-name arg-name)))

(defn ^:private compile-field
  "Rewrites the type of the field, and the type of any arguments."
  [type-def field-name field-def]
  (-> field-def
      rewrite-type
      (assoc :field-name field-name
             :qualified-field-name (keyword (-> type-def :type-name name)
                                            (name field-name)))
      (update :args #(map-kvs (fn [arg-name arg-def]
                                [arg-name (compile-arg arg-name arg-def)])
                              %))))

(defn ^:private wrap-resolver-to-ensure-resolver-result
  [resolver]
  (cond
    ;; The FieldResolver protocol allows a record (e.g., a component) to act as a field
    ;; resolver. This is where we turn it into a function. We can't tell whether
    ;; the method will return a ResolverResult or a bare value, so it will end up on
    ;; the less efficient path (the :else clause).
    ;; This also works with reify-ed instances of FieldResolver.
    (satisfies? resolve/FieldResolver resolver)
    (recur (resolve/as-resolver-fn resolver))

    ;; If a resolver reports its type as ResolverResult, then we don't
    ;; need to wrap it. This can really add up for all the default resolvers.
    ;; It's not so important for general resolvers.
    (-> resolver meta :tag (identical? ResolverResult))
    resolver

    ;; This is the "less efficient" path, as the result has to be tested to see
    ;; it is is a resolver result or not.
    :else
    (fn [context args value]
      (let [raw-value (resolver context args value)
            is-result? (is-resolver-result? raw-value)]
        (if is-result?
          raw-value
          (resolve-as raw-value))))))

(defn ^:no-doc floor-selector
  [selector-context]
  (let [callback (:callback selector-context)]
    (callback selector-context)))

(defn ^:private selector-error
  [selector-context error]
  (let [callback (:callback selector-context)]
    (-> selector-context
        (assoc
          :resolved-value nil
          :resolved-type nil)
        (update :errors conj error)
        callback)))

(defn ^:private create-root-selector
  "Creates a selector function for the :root kind, which is the point at which
  a type refers to something in the schema.

  type - object definition containing the field
  field - field definition
  field-type-name - from the root :root kind "
  [schema object-def field-def field-type-name]
  (let [field-name (:field-name field-def)
        field-type (get schema field-type-name)
        _ (when (nil? field-type)
            (throw (ex-info (format "Field %s of type %s references unknown type %s."
                                    (q field-name)
                                    (-> object-def :type-name q)
                                    (-> field-def :type q))
                            {:field field-def
                             :field-name field-name
                             :schema-types (type-map schema)})))
        category (:category field-type)

        ;; Build up layers of checks and other logic and a series of chained selector functions.
        ;; Normally, don't redefine local symbols, but here is makes it easier to follow and less
        ;; brittle.

        selector floor-selector

        selector (if (= :scalar category)
                   (let [serializer (:serialize field-type)]
                     (fn select-coerion [selector-context]
                       (let [{:keys [resolved-value]} selector-context
                             serialized (s/conform serializer resolved-value)]
                         (cond

                           (= serialized :clojure.spec.alpha/invalid)
                           (selector-error selector-context (error "Invalid value for a scalar type."
                                                                   {:type field-type-name
                                                                    :value (pr-str resolved-value)}))

                           (is-coercion-failure? serialized)
                           (selector-error selector-context serialized)

                           :else
                           (selector (assoc selector-context :resolved-value serialized))))))
                   selector)

        selector (if (= :enum category)
                   (let [possible-values (-> field-type :values set)]
                     (fn validate-enum [{:keys [resolved-value]
                                         :as selector-context}]
                       (cond-let
                         (nil? resolved-value)
                         (selector selector-context)

                         :let [keyword-value (as-keyword resolved-value)]

                         (not (possible-values keyword-value))
                         (throw (ex-info "Field resolver returned an undefined enum value."
                                         {:resolved-value resolved-value
                                          :enum-values possible-values}))

                         :else
                         (selector (assoc selector-context :resolved-value keyword-value)))))
                   selector)

        union-or-interface? (#{:interface :union} category)

        selector (if union-or-interface?
                   (let [member-types (:members field-type)]
                     (fn select-allowed-types [{:keys [resolved-type resolved-value]
                                                :as selector-context}]
                       (cond

                         (or (nil? resolved-value)
                             (contains? member-types resolved-type))
                         (selector selector-context)

                         (nil? resolved-type)
                         (selector-error selector-context (error "Field resolver returned an instance not tagged with a schema type."))

                         :else
                         (selector-error selector-context (error "Value returned from resolver has incorrect type for field."
                                                                 {:field-type field-type-name
                                                                  :actual-type resolved-type
                                                                  :allowed-types member-types})))))
                   selector)


        type-map (when union-or-interface?
                   (let [member-types (:members field-type)
                         member-objects (map schema member-types)
                         type-map (reduce (fn [m {:keys [tag type-name]}]
                                            (if tag
                                              (assoc m tag type-name)
                                              m))
                                          {}
                                          member-objects)]
                     (when (seq type-map)
                       type-map)))

        selector (fn select-unwrap-tagged-type [selector-context]
                   (cond-let
                     ;; Use explicitly tagged value (this usually applies to Java objects
                     ;; that can't provide meta data).
                     :let [resolved-value (:resolved-value selector-context)]
                     (is-tagged-value? resolved-value)
                     (selector (assoc selector-context
                                      :resolved-value (extract-value resolved-value)
                                      :resolved-type (extract-type-tag resolved-value)))

                     ;; Check for explicit meta-data:

                     :let [type-name (-> resolved-value meta ::type-name)]

                     (some? type-name)
                     (selector (assoc selector-context :resolved-type type-name))

                     ;; Use, if available, the mapping from tag to object that might be provided
                     ;; for some objects.
                     :let [resolved-type (when type-map
                                           (->> resolved-value
                                                class
                                                (get type-map)))]

                     (some? resolved-type)
                     (selector (assoc selector-context :resolved-type resolved-type))

                     ;; Let a later stage fail if it is a union or interface and there's no explicit
                     ;; type.
                     :else
                     (selector selector-context)))


        selector (if (#{:object :input-object} category)
                   (fn select-apply-static-type [selector-context]
                     ;; TODO: Maybe a check that if the resolved value is tagged, that the tag matches the expected tag?
                     (selector (assoc selector-context :resolved-type field-type-name)))
                   selector)]

    (fn select-require-single-value [{:keys [resolved-value]
                                      :as selector-context}]
      (if (sequential-or-set? resolved-value)
        (selector-error selector-context
                        (error "Field resolver returned a collection of values, expected only a single value."))
        (selector selector-context)))))

(defn ^:private assemble-selector
  "Assembles a selector function for a field.

   A selector function is invoked by the executor; it represents a pipeline of operations
   that occur before sub-selections occur on the resolved value.

   The selector is passed the resolved value and a callback.

   The resolved value is expected to be a seq (or nil) if the field type is list.

   The callback is passed the final resolved value.
   A second, optional, argument is an error map (or seq of error maps).

   The selector pipeline must return the value from the callback.

   type is a type map, as via rewrite-type."
  [schema object-type field type]
  (case (:kind type)

    :list
    (let [next-selector (assemble-selector schema object-type field (:type type))]
      (fn select-list [{:keys [resolved-value callback]
                        :as selector-context}]
        (cond
          (or (nil? resolved-value)
              (and (not (seq resolved-value))))
          (callback (assoc selector-context
                           :resolved-value []
                           :resolved-type nil))

          (not (sequential-or-set? resolved-value))
          (selector-error selector-context
                          (error "Field resolver returned a single value, expected a collection of values."))

          :else
          ;; So we have some privileged knowledge here: the callback returns a ResolverResult containing
          ;; the value. So we need to combine those together into a new ResolverResult.
          (let [unwrapper (fn [{:keys [resolved-value] :as selector-context}]
                            (if-not (instance? ResolveCommand resolved-value)
                              (next-selector selector-context)
                              (loop [v resolved-value
                                     sc selector-context]
                                (let [next-v (resolve/nested-value v)
                                      next-sc (resolve/apply-command v sc)]
                                  (if (instance? ResolveCommand next-v)
                                    (recur next-v next-sc)
                                    (next-selector (assoc next-sc :resolved-value next-v)))))))]
            (reduce #(combine-results conj %1 %2)
                    (resolve-as [])
                    (map-indexed
                      (fn [i v]
                        (unwrapper (-> selector-context
                                       (assoc :resolved-value v)
                                       (update :path conj i))))
                      resolved-value))))))

    :non-null
    (let [next-selector (assemble-selector schema object-type field (:type type))]
      (when (-> field :default-value some?)
        (throw (ex-info (format "Field %s of type %s is both non-nullable and has a default value."
                                (-> field :field-name q)
                                (-> object-type :type-name q))
                        {:field-name (:field-name field)
                         :field field})))
      (fn select-non-null [{:keys [resolved-value]
                            :as selector-context}]
        (cond
          (nil? resolved-value)
          (selector-error selector-context (error "Non-nullable field was null."))

          :else
          (next-selector selector-context))))

    :root                                                   ;;
    (create-root-selector schema object-type field (:type type))))

(defn ^:private default-field-description
  [schema type-def field-name]
  (->> type-def
       :implements
       (map schema)
       (keep #(get-in % [:fields field-name :description]))
       first))

(defn ^:private provide-default-arg-descriptions
  [field-def schema type-def]
  (let [interface-defs (->> type-def :implements (map schema))
        {:keys [field-name]} field-def
        reducer (fn [m arg-name arg-def]
                  (assoc m arg-name
                         (if (:description arg-def)
                           arg-def
                           (assoc arg-def :description
                                  (->> interface-defs
                                       (keep #(get-in % [:fields field-name :args arg-name :description]))
                                       first)))))]
    (update field-def :args #(reduce-kv reducer {} %))))

(defn ^:private prepare-field
  "Prepares a field for execution. Provides a default resolver, and wraps it to
  ensure it returns a ResolverResult.

  Inherits :documentation from matching inteface field as necessary.

  Adds a :selector function."
  [schema options type-def field-def]
  (let [provided-resolver (:resolve field-def)
        {:keys [default-field-resolver]} options
        {:keys [field-name description]} field-def
        type-name (:type-name type-def)
        base-resolver (if provided-resolver
                        provided-resolver
                        (default-field-resolver field-name))
        selector (assemble-selector schema type-def field-def (:type field-def))
        wrapped-resolver (cond-> (wrap-resolver-to-ensure-resolver-result base-resolver)
                           (nil? provided-resolver) (vary-meta assoc ::default-resolver? true))
        direct-fn (-> wrapped-resolver meta ::direct-fn)]
    (-> field-def
        (assoc :type-name type-name
               :description (or description
                                (default-field-description schema type-def field-name))
               :resolve wrapped-resolver
               :selector selector
               :direct-fn direct-fn)
        (provide-default-arg-descriptions schema type-def))))

;;-------------------------------------------------------------------------------
;; ## Compile schema

(defn ^:private xfer-types
  "Transfers values from the input map to the compiled schema, with checks for name collisions.

  The input map keys are type names, and the values are type definitions (matching the indicated
  category)."
  [compiled-schema input-map category]
  (reduce-kv (fn [s k v]
               (when (contains? s k)
                 (throw (ex-info (format "Name collision compiling schema. %s %s conflicts with existing %s."
                                         category
                                         (q k)
                                         (name (get-in s [k :category])))
                                 {:type-name k
                                  :category category
                                  :type v})))
               (assoc s k
                      (assoc v :category category
                             :type-name k)))
             compiled-schema
             input-map))


(defn ^:private types-with-category
  "Extracts from a compiled schema all the types with a matching category (:object, :interface, etc.)."
  [schema category]
  (->> schema
       vals
       (filter #(= category (:category %)))))

(defn ^:private compile-fields
  [type-def]
  (update type-def :fields #(map-kvs (fn [field-name field-def]
                                       [field-name (compile-field type-def field-name field-def)])
                                     %)))

(defmulti ^:private compile-type
  "Performs general compilation and validation of a type from the compiled schema.

  May throw an exception if the type fails validation.

  Because compilation of types occurs directly on the values, in an indeterminate order,
  some further validation and compilation must be delayed until after all types have been compiled."
  (fn [type schema] (:category type)))

(defmethod compile-type :default
  [type schema]
  type)

(defmethod compile-type :union
  [union schema]
  (let [members (-> union :members set)]
    (doseq [member members]
      (when-not (seq members)
        (throw (ex-info (format "Union %s does not define any members."
                                (-> union :type-name q))
                        {:union union})))
      (let [type (get schema member)]
        (cond
          (nil? type)
          (throw (ex-info (format "Union %s references unknown type %s."
                                  (-> union :type-name q)
                                  (q member))
                          {:union union
                           :schema-types (type-map schema)}))

          (not= :object (:category type))
          (throw (ex-info (format "Union %s includes member %s of type %s. Members must only be object types."
                                  (-> union :type-name q)
                                  (q member)
                                  (-> type :category name))
                          {:union union
                           :schema-types (type-map schema)})))))
    (assoc union :members members)))

(defn ^:private normalize-enum-value-def
  "The :values key of an enum definition is either a seq of enum values, or a seq of enum value defs.
  The enum values are just the keyword/symbol/string.
  The enum value defs have keys :enum-value and :description.
  This normalizes into the enum value def form, and ensures that the :enum-value key is a keyword."
  [value-def]
  (if (map? value-def)
    (update value-def :enum-value as-keyword)
    {:enum-value (as-keyword value-def)}))

(defmethod compile-type :enum
  [enum-def _]
  (let [value-defs (->> enum-def :values (mapv normalize-enum-value-def))
        values (mapv :enum-value value-defs)
        values-set (set values)
        ;; The detail for each value is the map that may includes :enum-value and
        ;; may include :description and/or :deprecated.
        details (reduce (fn [m {:keys [enum-value] :as detail}]
                               (assoc m enum-value detail))
                             {}
                             value-defs)]
    (when-not (= (count values) (count values-set))
      (throw (ex-info (format "Values defined for enum %s must be unique."
                              (-> enum-def :type-name q))
                      {:enum enum-def})))
    (assoc enum-def
           :values values
           :values-detail details
           :values-set values-set)))

(defmethod compile-type :scalar
  [scalar _]
  (let [{:keys [parse serialize]} scalar]
    (when-not (and parse serialize)
      (throw (ex-info "Scalars must declare both :parse and :serialize functions."
                      {:scalar scalar})))
    scalar))

(defmethod compile-type :object
  [object schema]
  (let [implements (->> object :implements (map as-keyword) set)
        tag (:tag object)
        ;; tag may be a symbol (to be converted to a class) or a class directly
        ;; Generally, a symbol if read from EDN, a Class if from Clojure source code constant
        tag-class (when tag
                    (if (class? tag)
                      tag
                      (try
                        (-> tag name Class/forName)
                        (catch Throwable t
                          (throw (ex-info (format "Object %s has tag %s, which can't be converted to a Java class."
                                                  (-> object :type-name q)
                                                  (q tag))
                                          {:object object}
                                          t))))))]
    (doseq [interface implements
            :let [type (get schema interface)]]
      (when-not type
        (throw (ex-info (format "Object %s extends interface %s, which does not exist."
                                (-> object :type-name q)
                                (q interface))
                        {:object object
                         :schema-types (type-map schema)})))
      (when-not (= :interface (:category type))
        (throw (ex-info (format "Object %s implements type %s, which is not an interface."
                                (-> object :type-name q)
                                (q interface))
                        {:object object
                         :schema-types (type-map schema)}))))
    (-> object
        (assoc :implements implements
               :tag tag-class)
        compile-fields)))

(defmethod compile-type :input-object
  [input-object schema]
  (let [input-object' (compile-fields input-object)]
    (doseq [[field-name field-def] (:fields input-object')
            :let [field-type-name (root-type-name field-def)
                  type-def (get schema field-type-name)
                  category (:category type-def)]]
      (when-not type-def
        (throw (ex-info (format "Field %s of input object %s references unknown type %s."
                                (q field-name)
                                (-> input-object :type-name q)
                                (q field-type-name))
                        {:input-object (:type-name input-object)
                         :field-name field-name
                         :schema-types (type-map schema)})))

      ;; Per 3.1.6, each field of an input object must be either a scalar, an enum,
      ;; or an input object.
      (when-not (#{:input-object :scalar :enum} category)
        (throw (ex-info (format "Field %s of input object %s must be type scalar, enum, or input-object."
                                (q field-name)
                                (-> input-object :type-name q))
                        {:input-object (:type-name input-object)
                         :field-name field-name
                         :field-type field-type-name}))))
    input-object'))

(defmethod compile-type :interface
  [interface schema]
  (compile-fields interface))

(defn ^:private extract-type-name
  "Navigates a type map down to the root kind and returns the type name."
  [type-map]
  (if (-> type-map :kind (= :root))
    (:type type-map)
    (recur (:type type-map))))

(defn ^:private verify-directives
  [schema object-def location]
  (doseq [directive-type (-> object-def :directives keys)
          :let [directive (get-in schema [::directive-defs directive-type])
                object-name (:type-name object-def)
                category (-> object-def :category name str/capitalize)]]
    (when-not directive
      (throw (ex-info (format "%s %s references unknown directive @%s."
                              category
                              (q object-name)
                              (name directive-type))
                      {:object-name object-name
                       :directive-type directive-type})))

    (when-not (-> directive :locations (contains? location))
      (throw (ex-info (format "Directive @%s on %s %s is not applicable."
                              (name directive-type)
                              category
                              (q object-name))
                      {:object-name object-name
                       :directive-type directive-type
                       :allowed-locations (:locations directive)})))))

(defn ^:private verify-fields-and-args
  "Verifies that the type of every field and every field argument is valid."
  [schema object-def]
  (let [object-type-name (:type-name object-def)
        directives (::directive-defs schema)
        location (if (= :input-object (:category object-def))
                   :input-field-definition
                   :field-definition)]
    (doseq [[field-name field-def] (:fields object-def)
            :let [field-type-name (extract-type-name (:type field-def))
                  qualified-name (keyword (name object-type-name)
                                          (name field-name))]]
      (when-not (get schema field-type-name)
        (throw (ex-info (format "Field %s references unknown type %s."
                                (q qualified-name)
                                (q field-type-name))
                        {:field-name qualified-name
                         :schema-types (type-map schema)})))

      (doseq [directive-type (-> object-def :directives keys)
              :let [directive (get directives directive-type)]]
        (when-not directive
          (throw (ex-info (format "Field %s references unknown directive @%s."
                                  (q qualified-name)
                                  (name directive-type))
                          {:field-name qualified-name
                           :directive-type directive-type})))

        (when-not (-> directive :locations (contains? location))
          (throw (ex-info (format "Direction @%s on field %s is not applicable."
                                  (name directive-type)
                                  (q qualified-name))
                          {:field-name qualified-name
                           :directive-type directive-type
                           :allowed-locations (:locations directive)}))))

      (doseq [[arg-name arg-def] (:args field-def)
              :let [arg-type-name (extract-type-name (:type arg-def))
                    arg-type-def (get schema arg-type-name)]]
        (when-not arg-type-def
          (throw (ex-info (format "Argument %s of field %s references unknown type %s."
                                  (q arg-name)
                                  (q qualified-name)
                                  (q arg-type-name))
                          {:field-name qualified-name
                           :arg-name arg-name
                           :schema-types (type-map schema)})))

        (when-not (#{:scalar :enum :input-object} (:category arg-type-def))
          (throw (ex-info (format "Argument %s of field %s is not a valid argument type."
                                  (q arg-name)
                                  (q qualified-name))
                          {:field-name qualified-name
                           :arg-name arg-name})))

        (doseq [directive-type (-> arg-def :directives keys)
                :let [directive (get directives directive-type)]]
          (when-not directive
            (throw (ex-info (format "Argument %s of field %s references unknown directive @%s."
                                    (q arg-name)
                                    (q qualified-name)
                                    (name directive-type))
                            {:field-name qualified-name
                             :arg-name arg-name
                             :directive-type directive-type})))

          (when-not (-> directive :locations (contains? :argument-definition))
            (throw (ex-info (format "Direction @%s on argument %s of field %s is not applicable."
                                    (name directive-type)
                                    (q arg-name)
                                    (q qualified-name))
                            {:field-name qualified-name
                             :directive-type directive-type
                             :allowed-locations (:locations directive)}))))))))

(defn ^:private prepare-and-validate-interfaces
  "Invoked after compilation to add a :members set identifying which concrete types implement
  the interface.  Peforms final verification of types in fields and field arguments."
  [schema]
  (let [objects (types-with-category schema :object)]
    (map-types schema :interface
               (fn [interface]
                 (verify-fields-and-args schema interface)
                 (verify-directives schema interface :interface)
                 (let [interface-name (:type-name interface)
                       implementors (->> objects
                                         (filter #(-> % :implements interface-name))
                                         (map :type-name)
                                         set)
                       fields' (map-vals #(assoc % :type-name interface-name)
                                         (:fields interface))]
                   (-> interface
                       (assoc :members implementors :fields fields')
                       (dissoc :resolve)))))))

(defn ^:private prepare-and-validate-object
  [schema object options]
  (verify-fields-and-args schema object)
  (verify-directives schema object (if (= :object (:category object))
                                     :object
                                     :input-object))
  (doseq [interface-name (:implements object)
          :let [interface (get schema interface-name)
                type-name (:type-name object)]
          [field-name interface-field] (:fields interface)
          :let [object-field (get-in object [:fields field-name])
                interface-field-args (:args interface-field)
                object-field-args (:args object-field)]]

    (when-not object-field
      (throw (ex-info "Missing interface field in object definition."
                      {:object type-name
                       :field-name field-name
                       :interface-name interface-name})))

    (when-not (is-assignable? schema interface-field object-field)
      (throw (ex-info "Object field is not compatible with extended interface type."
                      {:object type-name
                       :interface-name interface-name
                       :field-name field-name})))

    (when interface-field-args
      (doseq [interface-field-arg interface-field-args
              :let [[arg-name interface-arg-def] interface-field-arg
                    object-field-arg-def (get object-field-args arg-name)]]

        (when-not object-field-arg-def
          (throw (ex-info "Missing interface field argument in object definition."
                          {:object type-name
                           :field-name field-name
                           :interface-name interface-name
                           :argument-name arg-name})))

        (when-not (is-assignable? schema interface-arg-def object-field-arg-def)
          (throw (ex-info "Object field's argument is not compatible with extended interface's argument type."
                          {:object type-name
                           :interface-name interface-name
                           :field-name field-name
                           :argument-name arg-name})))))

    (when-let [additional-args (seq (difference (into #{} (keys object-field-args))
                                                (into #{} (keys interface-field-args))))]
      (doseq [additional-arg-name additional-args
              :let [arg-kind (get-in object-field-args [additional-arg-name :type :kind])]]
        (when (= arg-kind :non-null)
          (throw (ex-info "Additional arguments on an object field that are not defined in extended interface cannot be required."
                          {:object type-name
                           :interface-name interface-name
                           :field-name field-name
                           :argument-name additional-arg-name}))))))

  (update object :fields #(reduce-kv (fn [m field-name field]
                                       (assoc m field-name
                                              (prepare-field schema options object field)))
                                     {}
                                     %)))

(defn ^:private prepare-and-validate-objects
  "Comes very late in the compilation process to prepare objects, including validation that
  all implemented interface fields are present in each object."
  [schema category options]
  (map-types schema category
             #(prepare-and-validate-object schema % options)))

(def ^:private default-subscription-resolver

  ^ResolverResult
  (fn [_ _ value]
    (resolve-as value)))

(defn ^:private add-root
  "Adds a root object for 'extra' operations (e.g., the :queries map in the input schema)."
  [compiled-schema object-name object-description fields]
  (assoc compiled-schema object-name
         {:category :object
          :type-name object-name
          :description object-description
          :fields fields}))

(defn ^:private merge-root
  "Used after the compile-type stage, to merge together the root objects, one possibly provided
  via the input schema :roots map, and the other built from the :queries, :mutations, or :subscriptions
  maps (the 'extra object').

  The object-name is the name provided in the :roots map; it may not exist (normally, this is because
  it gets a default name such as `QueryRoot`), in which case it is created."
  [compiled-schema root-name extra-object-name object-name]
  (let [root-object-def (get compiled-schema object-name)
        extra-object-def (get compiled-schema extra-object-name)
        merge-into (fn [fields more-fields]
                     (reduce-kv (fn [m k v]
                                  (when (contains? m k)
                                    (throw (ex-info (format "Name collision compiling schema. %s %s conflicts with %s."
                                                            (-> root-name name str/capitalize)
                                                            (q (:qualified-field-name v))
                                                            (q (get-in m [k :qualified-field-name])))
                                                    {:field-name k
                                                     :operation root-name})))
                                  (assoc m k v))
                                fields
                                more-fields))]
    (cond-let

      (nil? root-object-def)
      ;; Copy the extra object over as the missing root object.
      (let [extra-object-def (get compiled-schema extra-object-name)]
        (assoc compiled-schema object-name
               (assoc extra-object-def :type-name object-name)))

      :let [category (:category root-object-def)]

      (= :object category)
      ;; Merge in the extra fields into the actual object
      (update-in compiled-schema [object-name :fields] merge-into (:fields extra-object-def))

      (= :union category)
      ;; Lacinia's execution and introspection code is built on the idea of a single root object type for
      ;; each type of operation. To keep that working, we copy the fields of each member object in
      ;; the union into the extra object and then designate the extra object the root.
      (let [reduce* (fn [val f coll] (reduce f val coll))]
        (-> compiled-schema
            (update-in [extra-object-name :fields] reduce* (fn [fields member-type-name]
                                                             (merge-into fields (get-in compiled-schema [member-type-name :fields])))
                       (:members root-object-def))
            (assoc-in [::roots root-name] extra-object-name)))

      :else
      (throw (ex-info (format "Type %s (a %s operation root) must be a union or object, not %s."
                              (q object-name)
                              (name root-name)
                              (name category))
                      {:type root-name
                       :category category})))))

(defn ^:private compile-directive-defs
  [schema directive-defs]
  (let [compile-directive-arg (fn [arg-name arg-def]
                                (let [arg-def' (compile-arg arg-name arg-def)
                                      arg-type-name (extract-type-name arg-def')
                                      arg-type (get schema arg-type-name)]
                                  (when-not arg-type
                                    (throw (ex-info "Unknown argument type."
                                                    {:arg-name arg-name
                                                     :arg-type-name arg-type-name
                                                     :schema-types (type-map schema)})))
                                  (when-not (= :scalar (:category arg-type))
                                    (throw (ex-info "Directive argument is not a scalar type."
                                                    {:arg-name arg-name
                                                     :arg-type-name arg-type-name
                                                     :schema-types (type-map schema)})))
                                  [arg-name arg-def']))
        compile-directive-args (fn [directive-type directive-def]
                                 (try
                                   [directive-type (update directive-def :args #(map-kvs compile-directive-arg %))]))]
    (assoc schema ::directive-defs
           (map-kvs compile-directive-args
                    (assoc directive-defs
                           :deprecated {:args {:reason {:type 'String}}
                                        :locations #{:field-definition :enum-value}})))))

(defn ^:private validate-unions
  [schema]
  (doseq [u (->> schema
                 vals
                 (filter #(-> % :category (= :union))))
          directive-type (-> u :directives keys)
          :let [directive (get-in schema [::directive-defs directive-type])]]
    (when-not directive
      (throw (ex-info (format "Union %s references unknown directive %@."
                              (-> u :type-name q)
                              (name directive-type))
                      {:union (:type-name q)
                       :directive-type directive-type})))

    (when-not (contains? (:locations directive) :union)
      (throw (ex-info (format "Union %s references directive %@ which is not applicable."
                              (-> u :type-name q)
                              (name directive-type))
                      {:union (:type-name q)
                       :directive-type directive-type}))))

  schema)

(defn ^:private construct-compiled-schema
  [schema options]
  ;; Note: using merge, not two calls to xfer-types, since want to allow
  ;; for overrides of the built-in scalars without a name conflict exception.
  (let [merged-scalars (merge default-scalar-transformers
                              (:scalars schema))
        {:keys [query mutation subscription]
         :or {query :QueryRoot
              mutation :MutationRoot
              subscription :SubscriptionRoot}} (map-vals as-keyword (:roots schema))
        defaulted-subscriptions (->> schema
                                     :subscriptions
                                     (map-vals #(if-not (:resolve %)
                                                  (assoc % :resolve default-subscription-resolver)
                                                  %)))]
    (-> {::roots {:query query
                  :mutation mutation
                  :subscription subscription}}
        (xfer-types merged-scalars :scalar)
        (xfer-types (:enums schema) :enum)
        (xfer-types (:unions schema) :union)
        (xfer-types (:objects schema) :object)
        (xfer-types (:interfaces schema) :interface)
        (xfer-types (:input-objects schema) :input-object)
        (add-root :__Queries "Root of all queries." (:queries schema))
        (add-root :__Mutations "Root of all mutations." (:mutations schema))
        (add-root :__Subscriptions "Root of all subscriptions." defaulted-subscriptions)
        (as-> s
              (map-vals #(compile-type % s) s))
        (merge-root :query :__Queries query)
        (merge-root :mutation :__Mutations mutation)
        (merge-root :subscription :__Subscriptions subscription)
        (compile-directive-defs (:directive-defs schema))
        (prepare-and-validate-interfaces)
        (prepare-and-validate-objects :object options)
        (prepare-and-validate-objects :input-object options)
        validate-unions
        map->CompiledSchema)))

(defn default-field-resolver
  "The default for the :default-field-resolver option, this uses the field name as the key into
  the resolved value."
  [field-name]
  ^{:tag ResolverResult
    ::direct-fn field-name}
  (fn default-resolver [_ _ v]

    (resolve-as (get v field-name))))

(defn hyphenating-default-field-resolver
  "An alternative to [[default-field-resolver]], this converts underscores in the field name
  into hyphens.  At one time, this was the default behavior."
  {:added "0.17.0"}
  [field-name]
  (-> field-name
      name
      (str/replace "_" "-")
      keyword
      default-field-resolver))

(def ^:private default-compile-opts
  {:default-field-resolver default-field-resolver})

(s/def ::compile-args
  (s/cat :schema ::schema-object
         :options (s/? (s/nilable ::compile-options))))

(defn compile
  "Compiles an schema, verifies its correctness, and prepares it for query execution.
  The compiled schema is in an entirely different format than the input schema.

  The format of the compiled schema is subject to change without notice.

  This function explicitly validates its arguments using clojure.spec.

  Compile options:

  :default-field-resolver

  : A function that accepts a field name (as a keyword) and converts it into the
    default field resolver; this defaults to [[default-field-resolver]].

  Produces a form ready for use in executing a query."
  ([schema]
   (compile schema nil))
  ([schema options]
   ;; This is based on clojure.spec's assert, but is always on
   ;; the single branch alt adds :args to the explain path as expected in tests
   (when-let [ed (s/explain-data ::compile-args [schema options])]
     (throw (ex-info
             (str "Arguments to compile do not conform to spec:\n" (with-out-str (s/explain-out ed)))
             ed)))
   (let [options' (merge default-compile-opts options)
         introspection-schema (introspection/introspection-schema)]
     (-> schema
         (deep-merge introspection-schema)
         (construct-compiled-schema options')))))

;; The compiled schema tends to be huge and unreadable. It clutters exception output.
;; The following defmethods reduce its output to a stub.

(def ^{:dynamic true
       :added "0.25.0"} *verbose-schema-printing*
  "If bound to true, then the compiled schema prints and pretty-prints like an ordinary map,
  which is sometimes useful during development. When false (the default) the schema
  output is just a placeholder."
  false)

(defmethod print-method CompiledSchema
  [schema ^Writer w]
  (if *verbose-schema-printing*
    (print-method (into {} schema) w)
    (.write w "#CompiledSchema<>")))

(defmethod pprint/simple-dispatch CompiledSchema
  [schema]
  (if *verbose-schema-printing*
    (pprint/simple-dispatch (into {} schema))
    (pr schema)))
