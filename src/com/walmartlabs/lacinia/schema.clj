(ns com.walmartlabs.lacinia.schema
  "Responsible for constructing and validating the GraphQL schema.

  GraphQL schema starts in a format easy to read and maintain as an EDN file.

  Compiling the schema performs a number of validations and reorganizations to
  make query execution faster and simpler, such as generating a flatter structure for the
  schema, and pre-computing many defaults."
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.spec :as s]
    [com.walmartlabs.lacinia.introspection :as introspection]
    [com.walmartlabs.lacinia.constants :as constants]
    [com.walmartlabs.lacinia.internal-utils
     :refer [map-vals map-kvs filter-vals deep-merge q
             is-internal-type-name? sequential-or-set? as-keyword
             combine-results
             ->TaggedValue is-tagged-value? extract-value extract-type-tag]]
    [com.walmartlabs.lacinia.resolve :refer [ResolverResult resolve-as]]
    [clojure.string :as str])
  (:import
    (com.walmartlabs.lacinia.resolve ResolverResultImpl)))

;; When using Clojure 1.9 alpha, the dependency on clojure-future-spec can be excluded,
;; and this code will not trigger; any? will come out of clojure.core as normal.
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [any?]]))

;;-------------------------------------------------------------------------------
;; ## Helpers

(s/check-asserts true)

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

  Returns a new wrapper instance that combines the value and the type name."
  [x type-name]
  ;; In some cases, the resolver for a field may tag a value even though it
  ;; gets re-tagged automatically.
  (if (is-tagged-value? x)
    (if (= type-name (extract-type-tag x))
      x
      (->TaggedValue (extract-value x) type-name))
    (->TaggedValue x type-name)))

(defn type-map
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

(defn ^:private named?
  "True if a string, symbol or keyword."
  [v]
  (or (string? v)
      (symbol? v)
      (keyword? v)))

;;-------------------------------------------------------------------------------
;; ## Validations

;; This can be expanded at some point
(s/def :type/type some?)
(s/def :type/resolve (s/or :type/resolve-keyword keyword?
                           :type/resolve-callback fn?))
(s/def :type/field (s/keys :opt-un [:type/description
                                    :type/resolve
                                    :type/args]
                           :req-un [:type/type]))
(s/def :type/fields (s/map-of keyword? :type/field))
(s/def :type/implements (s/coll-of keyword?))
(s/def :type/description string?)
;; Given that objects merge in the field definitions from their containing interfaces,
;; it is reasonable for ojects to only optionally define fields.
(s/def :type/object (s/keys :opt-un [:type/fields
                                     :type/implements
                                     :type/description]))
(s/def :type/interface (s/keys :opt-un [:type/description
                                        :type/fields]))
;; A list of keyword identifying objects that are part of a union.
(s/def :type/members (s/and (s/coll-of keyword?)
                            seq))
(s/def :type/union (s/keys :opt-un [:type/description]
                           :req-un [:type/members]))
(s/def :type/values (s/and (s/coll-of named?)
                           seq))
(s/def :type/enum (s/keys :opt-un [:type/description]
                          :req-un [:type/values]))
(s/def :type/input-object (s/keys :opt-un [:type/description]))
(s/def :type/parse s/spec?)
(s/def :type/serialize s/spec?)
(s/def :type/scalar (s/keys :opt-un [:type/description]
                            :req-un [:type/parse
                                     :type/serialize]))
(s/def :type/scalars (s/map-of keyword? :type/scalar))
(s/def :type/interfaces (s/map-of keyword? :type/interface))
(s/def :type/objects (s/map-of keyword? :type/object))
(s/def :type/input-objects (s/map-of keyword? :type/input-object))
(s/def :type/enums (s/map-of keyword? :type/enum))
(s/def :type/unions (s/map-of keyword? :type/union))

(s/def ::schema-object
  (s/keys :opt-un [:type/scalars
                   :type/interfaces
                   :type/objects
                   :type/input-objects
                   :type/enums
                   :type/unions]))

(s/def :graphql/type-decl
  (s/or :base-type (fn [x] (or (keyword? x) (symbol? x)))
        :complex-type (s/cat :wrapping-type #{'list 'non-null}
                             :type :graphql/type-decl)))

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

(defn root-type-name
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
  [field-name field-def]
  (-> field-def
      rewrite-type
      (assoc :field-name field-name)
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

(defn ^:private compose-selectors
  [next-selector selector-wrapper]
  (if (some? selector-wrapper)
    (fn invoke-wrapper [resolved-value resolved-type callback]
      (selector-wrapper resolved-value resolved-type next-selector callback))
    next-selector))

(defn ^:nodoc floor-selector
  [resolved-value resolved-type callback]
  (callback resolved-value resolved-type))

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

        ;; Build up layers of checks and other logic.
        coercion-wrapper (when (= :scalar category)
                           (let [serializer (:serialize field-type)]
                             (fn [resolved-value resolved-type next-selector callback]
                               (let
                                 [serialized (s/conform serializer resolved-value)]
                                 (cond

                                   (= serialized :clojure.spec/invalid)
                                   (callback nil nil (error "Invalid value for a scalar type."
                                                            {:type field-type-name
                                                             :value (pr-str resolved-value)}))

                                   (is-coercion-failure? serialized)
                                   (callback nil nil serialized)

                                   :else
                                   (next-selector serialized resolved-type callback))))))

        allowed-types-wrapper (when (#{:interface :union} category)
                                (let [member-types (:members field-type)]
                                  (fn [resolved-value resolved-type next-selector callback]
                                    (cond

                                      (contains? member-types resolved-type)
                                      (next-selector resolved-value resolved-type callback)

                                      (nil? resolved-type)
                                      (callback nil nil (error "Field resolver returned an instance not tagged with a schema type."))

                                      :else
                                      (callback nil nil (error "Value returned from resolver has incorrect type for field."
                                                               {:field-type field-type-name
                                                                :actual-type resolved-type
                                                                :allowed-types member-types}))))))
        unwrap-tagged-type-wrapper (fn [resolved-value resolved-type next-selector callback]
                                     (if (is-tagged-value? resolved-value)
                                       (next-selector (extract-value resolved-value)
                                                      (extract-type-tag resolved-value) callback)
                                       (next-selector resolved-value resolved-type callback)))
        apply-static-type-wrapper (when (#{:object :input-object} category)
                                    (fn [resolved-value resolved-type next-selector callback]
                                      ;; TODO: Maybe a check that if the resolved value is tagged, that the tag matches
                                      ;; the expected tag?
                                      (next-selector resolved-value field-type-name callback)))
        single-result-wrapper (fn [resolved-value resolved-type next-selector callback]
                                (if (sequential-or-set? resolved-value)
                                  (callback nil nil (error "Field resolver returned a collection of values, expected only a single value."))
                                  (next-selector resolved-value resolved-type callback)))]
    (reduce compose-selectors floor-selector [coercion-wrapper
                                              allowed-types-wrapper
                                              unwrap-tagged-type-wrapper
                                              apply-static-type-wrapper
                                              single-result-wrapper])))

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
      (fn [resolved-value resolved-type callback]
        (cond
          (nil? resolved-value)
          (callback ::empty-list nil)

          (not (sequential-or-set? resolved-value))
          (callback nil nil (error "Field resolver returned a single value, expected a collection of values."))

          :else
          ;; So we have some privileged knowledge here: the callback returns a ResolverResult containing
          ;; the value. So we need to combine those together into a new ResolverResult.
          (reduce #(combine-results conj %1 %2)
                  (resolve-as [])
                  (mapv #(next-selector % resolved-type callback) resolved-value)))))

    :non-null
    (let [next-selector (assemble-selector schema object-type field (:type type))]
      (when (-> field :default-value some?)
        (throw (ex-info (format "Field %s of type %s is both non-nullable and has a default value."
                                (-> field :field-name q)
                                (-> object-type :type-name q))
                        {:field-name (:field-name field)
                         :field field})))
      (fn [resolved-value resolved-type callback]
        (cond
          (nil? resolved-value)
          (callback nil nil (error "Non-nullable field was null."))

          :else
          (next-selector resolved-value resolved-type callback))))

    :root                                                   ;;
    (create-root-selector schema object-type field (:type type))))

(defn ^:private prepare-field
  "Prepares a field for execution. Provides a default resolver, and wraps it to
  ensure it returns a ResolverResult.
  Adds a :selector function."
  [schema options containing-type field]
  (let [provided-resolver (:resolve field)
        {:keys [default-field-resolver decorator]} options
        field-name (:field-name field)
        base-resolver (if provided-resolver
                        (decorator (:type-name containing-type) field-name provided-resolver)
                        (default-field-resolver field-name))
        selector (assemble-selector schema containing-type field (:type field))
        wrapped-resolver (cond-> (wrap-resolver-to-ensure-resolver-result base-resolver)
                           (nil? provided-resolver) (vary-meta assoc ::default-resolver? true))]
    (assoc field
           :resolve wrapped-resolver
           :selector selector)))

;;-------------------------------------------------------------------------------
;; ## Compile schema

(defn ^:private xfer-types
  "Transfers values from the input map to the compiled schema, with checks for name collisions.

  The input map keys are type names, and the values are type definitions (matching the indicated
  category)."
  [compiled-schema input-map category]
  (reduce-kv (fn [s k v]
               (when (contains? s k)
                 (throw (ex-info (format "Name collision compiling schema. %s `%s' conflicts with existing %s."
                                         category
                                         (name k)
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
  [type]
  (update type :fields #(map-kvs (fn [field-name field]
                                   [field-name (compile-field field-name field)])
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
  [enum schema]
  (let [values (->> enum :values (mapv as-keyword))
        values-set (set values)]
    (when-not (= (count values) (count values-set))
      (throw (ex-info (format "Values defined for enum %s must be unique."
                              (-> enum :type-name q))
                      {:enum enum})))
    (assoc enum
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
  (let [implements (-> object :implements set)]
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

;; TODO: I think these checks go into create-unit-selector
(defn ^:private verify-fields-and-args
  "Verifies that the type of every field and every field argument is valid."
  [schema object-def]
  (let [object-type-name (:type-name object-def)]
    (doseq [[field-name field-def] (:fields object-def)
            :let [field-type-name (extract-type-name (:type field-def))]]
      (when-not (get schema field-type-name)
        (throw (ex-info (format "Field %s in type %s references unknown type %s."
                                (q field-name)
                                (q object-type-name)
                                (q field-type-name))
                        {:field-name field-name
                         :object-type object-type-name
                         :field field-def
                         :schema-types (type-map schema)})))

      (doseq [[arg-name arg-def] (:args field-def)
              :let [arg-type-name (extract-type-name (:type arg-def))
                    arg-type-def (get schema arg-type-name)]]
        (when-not arg-type-def
          (throw (ex-info (format "Argument %s of field %s in type %s references unknown type %s."
                                  (q arg-name)
                                  (q field-name)
                                  (q object-type-name)
                                  (q arg-type-name))
                          {:field-name field-name
                           :object-type object-type-name
                           :arg-name arg-name
                           :schema-types (type-map schema)})))

        (when-not (#{:scalar :enum :input-object} (:category arg-type-def))
          (throw (ex-info (format "Argument %s of field %s in type %s is not a valid argument type."
                                  (q arg-name)
                                  (q field-name)
                                  (q object-type-name))
                          {:field-name field-name
                           :arg-name arg-name
                           :object-type object-type-name})))))))

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
                                         set)]
                   (-> interface
                       (assoc :members implementors)
                       (dissoc :resolve)))))))

(defn ^:private prepare-and-validate-object
  [schema object options]
  (verify-fields-and-args schema object)
  (doseq [interface-name (:implements object)
          :let [interface (get schema interface-name)]
          [field-name interface-field] (:fields interface)
          :let [object-field (get-in object [:fields field-name])]]

    ;; TODO: I believe we are missing a check that arguments on the field are
    ;; compatible with arguments on the interface field.

    (when-not object-field
      (throw (ex-info "Missing interface field in object definition."
                      {:object (:type-name object)
                       :field-name field-name
                       :interface-name interface-name})))

    (when-not (is-assignable? schema interface-field object-field)
      (throw (ex-info "Object field is not compatible with extended interface type."
                      {:object (:type-name object)
                       :interface-name interface-name
                       :field-name field-name}))))

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

(defn ^:private construct-compiled-schema
  [schema options]
  ;; Note: using merge, not two calls to xfer-types, since want to allow
  ;; for overrides of the built-in scalars without a name conflict exception.
  (let [merged-scalars (merge default-scalar-transformers
                              (:scalars schema))]
    (-> {constants/query-root {:category :object
                               :type-name constants/query-root
                                 :description "Root of all queries."}
         constants/mutation-root {:category :object
                                  :type-name constants/mutation-root
                                   :description "Root of all mutations."}}
        (xfer-types merged-scalars :scalar)
        (xfer-types (:enums schema) :enum)
        (xfer-types (:unions schema) :union)
        (xfer-types (:objects schema) :object)
        (xfer-types (:interfaces schema) :interface)
        (xfer-types (:input-objects schema) :input-object)
        (assoc-in [constants/query-root :fields] (:queries schema))
        (assoc-in [constants/mutation-root :fields] (:mutations schema))
        (as-> s
              (map-vals #(compile-type % s) s))
        (prepare-and-validate-interfaces)
        (prepare-and-validate-objects :object options)
        (prepare-and-validate-objects :input-object options))))

(s/def ::resolver
  (s/fspec :args (s/cat ::context (s/keys)
                        ::arguments (s/map-of keyword? any?)
                        ::value any?)
           :ret any?))

(s/def ::default-field-resolver
  (s/fspec :args (s/cat :field keyword?)
           :ret ::resolver))

(s/def ::decorator
  (s/fspec :args (s/cat :object-name keyword? :field-name keyword? :resolver ::resolver)
           :ret ::resolver))

(s/def ::compile-options (s/keys :opt-un [::default-field-resolver
                                          ::decorator]))


(defn default-field-resolver
  "The default for the :default-field-resolver option, this uses the field name as the key into
  the resolved value."
  [field-name]
  ^ResolverResultImpl
  (fn [_ _ v]
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

(defn pass-thru-decorator
  "The default decorator for field resolvers, which returns the resolver unchanged."
  {:added "0.17.0"}
  [object-name field-name f]
  f)

(def ^:private default-compile-opts
  {:default-field-resolver default-field-resolver
   :decorator pass-thru-decorator})

(defn compile
  "Compiles a schema, verifies its correctness, and inlines all types.

  Compile options:

  :default-field-resolver

  : A function that accepts a field name (as a keyword) and converts it into the
    default field resolver; this defaults to [[default-field-resolver]].

  :decorator

  : A function that accepts a object name, field name, and field resolver function and
    returns a new field resolver function (of the same one).

  Produces a form ready to be used in executing a query."
  ([schema]
   (compile schema {}))
  ([schema options]
   (let [options' (merge default-compile-opts options)
         introspection-schema (introspection/introspection-schema)]
     (-> schema
         (deep-merge introspection-schema)
         (construct-compiled-schema options')))))

(s/fdef compile
        :args (s/cat :schema ::schema-object
                     :options (s/? ::compile-options)))
