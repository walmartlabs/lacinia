(ns com.walmartlabs.lacinia.schema
  "Responsible for constructing and validating the GraphQL schema.

  GraphQL schema starts in a format easy to read and maintain as an EDN file.

  Compiling the schema performs a number of validations and reorganizations to
  make query execution faster and simpler, such as generating a flatter structure for the
  schema, and pre-computing many defaults."
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.introspection :as introspection]
    [com.walmartlabs.lacinia.constants :as constants]
    [com.walmartlabs.lacinia.internal-utils
     :refer [map-vals map-kvs filter-vals deep-merge q
             is-internal-type-name? sequential-or-set? as-keyword
             cond-let ->TaggedValue is-tagged-value? extract-value extract-type-tag]]
    [com.walmartlabs.lacinia.resolve :refer [ResolverResult resolve-as combine-results]]
    [clojure.string :as str]
    [clojure.set :refer [difference]]
    [clojure.spec.test.alpha :as stest])
  (:import
    (com.walmartlabs.lacinia.resolve ResolverResultImpl)
    (clojure.lang IObj)))

;; When using Clojure 1.9 alpha, the dependency on clojure-future-spec can be excluded,
;; and this code will not trigger; any? will come out of clojure.core as normal.
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [any? simple-keyword? simple-symbol?]]))

;;-------------------------------------------------------------------------------
;; ## Helpers

(s/check-asserts true)

(def ^:private graphql-identifier #"(?i)_*[a-z][a-zA-Z0-9_-]*")

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

(defn ^:private graphql-identifier?
  "Expects a conformed value (turns out to be a Map$Entry such as `[:keyword :foo]`)
  and validates that the value is a valid GraphQL identifier."
  [[_ v]]
  (boolean
    (re-matches graphql-identifier (name v))))

;;-------------------------------------------------------------------------------
;; ## Validations

(s/def ::schema-key (s/and simple-keyword?
                           ::graphql-identifier))
(s/def ::graphql-identifier #(re-matches graphql-identifier (name %)))
(s/def ::identifier (s/and (s/or :keyword simple-keyword?
                                 :symbol simple-symbol?)
                           graphql-identifier?))
(s/def ::type (s/or :base-type ::identifier
                    :wrapping-type (s/cat :modifier #{'list 'non-null}
                                          :type ::type)))
(s/def ::arg (s/keys :req-un [::type]
                     :opt-un [::description]))
(s/def ::args (s/map-of ::schema-key ::arg))
;; Defining these callbacks in spec has been a challenge. At some point,
;; we can expand this to capture a bit more about what a field resolver
;; is passed and should return.
(s/def ::resolve fn?)
(s/def ::field (s/keys :opt-un [::description
                                ::resolve
                                ::args]
                       :req-un [::type]))
(s/def ::operation (s/keys :opt-un [::description
                                    ::args]
                           :req-un [::type
                                    ::resolve]))
(s/def ::fields (s/map-of ::schema-key ::field))
(s/def ::implements (s/coll-of ::identifier))
(s/def ::description string?)
(s/def ::object (s/keys :req-un [::fields]
                        :opt-un [::implements
                                 ::description]))
;; Here we'd prefer a version of ::fields where :resolve was not defined.
(s/def ::interface (s/keys :opt-un [::description
                                    ::fields]))
;; A list of keyword identifying objects that are part of a union.
(s/def ::members (s/and (s/coll-of ::identifier)
                        seq))
(s/def ::union (s/keys :opt-un [::description]
                       :req-un [::members]))
(s/def ::enum-value (s/and (s/or :string string?
                                 :keyword simple-keyword?
                                 :symbol simple-symbol?)
                           graphql-identifier?))
(s/def ::values (s/and (s/coll-of ::enum-value)
                       seq))
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
(s/def ::stream fn?)

(s/def ::queries (s/map-of ::schema-key ::operation))
(s/def ::mutations (s/map-of ::schema-key ::operation))

(s/def ::subscription (s/keys :opt-un [::description
                                       ::resolve
                                       ::args]
                              :req-un [::type
                                       ::stream]))

(s/def ::subscriptions (s/map-of ::schema-key ::subscription))

(s/def ::schema-object
  (s/keys :opt-un [::scalars
                   ::interfaces
                   ::objects
                   ::input-objects
                   ::enums
                   ::unions
                   ::queries
                   ::mutations
                   ::subscriptions]))

;; Again, this can be fleshed out once we have a handle on defining specs for
;; functions:
(s/def ::default-field-resolver fn?)

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
    (list? type)
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
  ;; If a resolver reports its type as ResolverResult, then we don't
  ;; need to wrap it. This can really add up for all the default resolvers.
  (if (-> resolver meta :tag (identical? ResolverResult))
    resolver
    (fn [context args value]
      (let [raw-value (resolver context args value)
            is-result? (when raw-value
                         ;; This is a little bit of optimization; satisfies? can
                         ;; be a bit expensive.
                         (or (instance? ResolverResultImpl raw-value)
                             (satisfies? ResolverResult raw-value)))]
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

        coercion (if (= :scalar category)
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
                           (floor-selector (assoc selector-context :resolved-value serialized))))))
                   floor-selector)

        allowed-types (if (#{:interface :union} category)
                        (let [member-types (:members field-type)]
                          (fn select-allowed-types [{:keys [resolved-type]
                                                     :as selector-context}]
                            (cond

                              (contains? member-types resolved-type)
                              (coercion selector-context)

                              (nil? resolved-type)
                              (selector-error selector-context (error "Field resolver returned an instance not tagged with a schema type."))

                              :else
                              (selector-error selector-context (error "Value returned from resolver has incorrect type for field."
                                                                      {:field-type field-type-name
                                                                       :actual-type resolved-type
                                                                       :allowed-types member-types})))))
                        coercion)

        unwrap-tagged-type (fn select-unwrap-tagged-type [selector-context]
                             (cond-let
                               :let [resolved-value (:resolved-value selector-context)]
                               (is-tagged-value? resolved-value)
                               (allowed-types (assoc selector-context
                                                     :resolved-value (extract-value resolved-value)
                                                     :resolved-type (extract-type-tag resolved-value)))

                               :let [type-name (-> resolved-value meta ::type-name)]

                               (some? type-name)
                               (allowed-types (assoc selector-context :resolved-type type-name))

                               :else
                               (allowed-types selector-context)))

        apply-static-type (if (#{:object :input-object} category)
                            (fn select-apply-static-type [selector-context]
                              ;; TODO: Maybe a check that if the resolved value is tagged, that the tag matches the expected tag?
                              (unwrap-tagged-type (assoc selector-context :resolved-type field-type-name)))
                            unwrap-tagged-type)]

    (fn select-require-single-value [{:keys [resolved-value]
                                      :as selector-context}]
      (if (sequential-or-set? resolved-value)
        (selector-error selector-context
                        (error "Field resolver returned a collection of values, expected only a single value."))
        (apply-static-type selector-context)))))

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
          (nil? resolved-value)
          (callback (assoc selector-context
                           :resolved-value []
                           :resolved-type nil))

          (not (sequential-or-set? resolved-value))
          (selector-error selector-context
                          (error "Field resolver returned a single value, expected a collection of values."))

          :else
          ;; So we have some privileged knowledge here: the callback returns a ResolverResult containing
          ;; the value. So we need to combine those together into a new ResolverResult.
          (reduce #(combine-results conj %1 %2)
                  (resolve-as [])
                  (mapv #(next-selector (assoc selector-context :resolved-value %))
                        resolved-value)))))

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

(defmethod compile-type :enum
  [enum-def schema]
  (let [values (->> enum-def :values (mapv as-keyword))
        values-set (set values)]
    (when-not (= (count values) (count values-set))
      (throw (ex-info (format "Values defined for enum %s must be unique."
                              (-> enum-def :type-name q))
                      {:enum enum-def})))
    (assoc enum-def
           :values values
           :values-set values-set)))

(defmethod compile-type :scalar
  [scalar schema]
  (let [{:keys [parse serialize]} scalar]
    (when-not (and parse serialize)
      (throw (ex-info "Scalars must declare both :parse and :serialize functions."
                      {:scalar scalar})))
    scalar))

(defmethod compile-type :object
  [object schema]
  (let [implements (->> object :implements (map as-keyword) set)]
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
        (assoc :implements implements)
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

(defn ^:private verify-fields-and-args
  "Verifies that the type of every field and every field argument is valid."
  [schema object-def]
  (let [object-type-name (:type-name object-def)]
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
                           :arg-name arg-name})))))))

(defn ^:private prepare-and-validate-interfaces
  "Invoked after compilation to add a :members set identifying which concrete types implement
  the interface.  Peforms final verification of types in fields and field arguments."
  [schema]
  (let [objects (types-with-category schema :object)]
    (map-types schema :interface
               (fn [interface]
                 (verify-fields-and-args schema interface)
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

(defn ^:private construct-compiled-schema
  [schema options]
  ;; Note: using merge, not two calls to xfer-types, since want to allow
  ;; for overrides of the built-in scalars without a name conflict exception.
  (let [merged-scalars (merge default-scalar-transformers
                              (:scalars schema))
        defaulted-subscriptions (->> schema
                                     :subscriptions
                                     (map-vals #(if-not (:resolve %)
                                                  (assoc % :resolve default-subscription-resolver)
                                                  %)))]
    (-> {constants/query-root {:category :object
                               :type-name constants/query-root
                                 :description "Root of all queries."}
         constants/mutation-root {:category :object
                                  :type-name constants/mutation-root
                                   :description "Root of all mutations."}
         constants/subscription-root {:category :object
                                      :type-name constants/subscription-root
                                      :description "Root of all subscriptions."}}
        (xfer-types merged-scalars :scalar)
        (xfer-types (:enums schema) :enum)
        (xfer-types (:unions schema) :union)
        (xfer-types (:objects schema) :object)
        (xfer-types (:interfaces schema) :interface)
        (xfer-types (:input-objects schema) :input-object)
        (assoc-in [constants/query-root :fields] (:queries schema))
        (assoc-in [constants/mutation-root :fields] (:mutations schema))
        (assoc-in [constants/subscription-root :fields] defaulted-subscriptions)
        ;; queries, mutations, and subscriptions are fields on special objects; a lot of
        ;; compilation occurs here along with ordinary objects.
        (as-> s
              (map-vals #(compile-type % s) s))
        (prepare-and-validate-interfaces)
        (prepare-and-validate-objects :object options)
        (prepare-and-validate-objects :input-object options))))

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

(defn compile
  "Compiles an schema, verifies its correctness, and prepares it for query execution.
  The compiled schema is in an entirely different format than the input schema.

  The format of the compiled schema is subject to change without notice.

  This function is always instrumented with Clojure spec: non-conforming
  input schemas will cause a spec validation exception to be thrown.

  Compile options:

  :default-field-resolver

  : A function that accepts a field name (as a keyword) and converts it into the
    default field resolver; this defaults to [[default-field-resolver]].

  Produces a form ready to be used in executing a query."
  ([schema]
   (compile schema nil))
  ([schema options]
   (let [options' (merge default-compile-opts options)
         introspection-schema (introspection/introspection-schema)]
     (-> schema
         (deep-merge introspection-schema)
         (construct-compiled-schema options')))))

(s/fdef compile
        :args (s/cat :schema ::schema-object
                     :options (s/? (s/nilable ::compile-options))))

;; Instrumenting compile ensures that a number of important checks occur.
;; It makes things slower, but that cost is endured once for a production app.
;; When doing REPL development, it is valuable to have the checks at compile, vs.
;; difficult to trace exceptions at runtime.

(stest/instrument `compile)
