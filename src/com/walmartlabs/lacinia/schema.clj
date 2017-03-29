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
     :refer [cond-let ensure-seq map-vals map-kvs filter-vals deep-merge q
             is-internal-type-name?]]
    [com.walmartlabs.lacinia.resolve :refer [ResolverResult resolved-value resolve-errors resolve-as]]
    [clojure.string :as str])
  (:import
    (com.walmartlabs.lacinia.resolve ResolverResultImpl)))

;; When using Clojure 1.9 alpha, the dependency on clojure-future-spec can be excluded,
;; an this code will not trigger; any? will come out of clojure.core as normal.
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [any?]]))

;;-------------------------------------------------------------------------------
;; ## Helpers

(s/check-asserts true)

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

(defn ^:private as-keyword
  [v]
  (cond
    (keyword? v) v

    (symbol? v) (-> v name keyword)

    (string? v) (keyword v)

    :else
    (throw (ex-info "Unexpected type value." {:type v}))))

(defn tag-with-type
  "Tags a value with a GraphQL type name, a keyword.
  The keyword should identify a specific concrete object
  (not an interface or union) in the relevent schema."
  [x type-name]
  (vary-meta x assoc ::graphql-type-name type-name))

(defn type-tag
  "Returns the GraphQL type tag, previously set with [[tag-with-type]], or nil if the tag is not present."
  [x]
  (-> x meta ::graphql-type-name))


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

(defn ^:private conformer
  "Creates a conformer that returns
  :clojure.spec/invalid when the value fails
  to conform."
  [f]
  (s/conformer
    (fn [x]
      (try
        (when (some? x)
          (f x))
        (catch Exception _
          ::s/invalid)))))

(defn ^:private coerce-to-int
  [v]
  (cond
    (number? v) (.intValue ^Number v)
    (string? v) (Integer/parseInt v)
    :else (throw (ex-info (str "Invalid Int value: " v) {:value v}))))

(defn ^:private coerce-to-float
  [v]
  (cond
    (number? v) (double v)
    (string? v) (Double/parseDouble v)
    :else (throw (ex-info (str "Invalid Float value: " v) {:value v}))))

(def default-scalar-transformers
  {:String {:parse (conformer str)
            :serialize (conformer str)}
   :Float {:parse (conformer #(Double/parseDouble %))
           :serialize (conformer coerce-to-float)}
   :Int {:parse (conformer #(Integer/parseInt %))
         :serialize (conformer coerce-to-int)}
   :Boolean {:parse (conformer #(Boolean/parseBoolean %))
             :serialize (conformer #(Boolean/valueOf %))}
   :ID {:parse (conformer str)
        :serialize (conformer str)}})

(defn ^:private error
  ([message]
   (error message nil))
  ([message data]
   [nil (merge {:message message} data)]))

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

(defn ^:private type-seq
  "Given a (possibly) complex type, eg. (list (non-null :foo)),
  return the flattened type sequence, eg. [list non-null :foo]"
  [type]
  (s/assert :graphql/type-decl type)
  (if (sequential? type)
    (flatten type)
    [type]))

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

(defn ^:private is-compatible-type? [schema i-type-name f-type-name]
  (or (= i-type-name f-type-name)
      (check-compatible (get schema i-type-name)
                        (get schema f-type-name))))

(defn ^:private is-assignable?
  "Returns true if the object field is type compatible with the interface field."
  [schema interface-field object-field]
  (and (= (:multiple? interface-field) (:multiple? object-field))
       (= (:non-nullable? interface-field) (:non-nullable? object-field))
       (is-compatible-type? schema (:type interface-field) (:type object-field))))

;;-------------------------------------------------------------------------------
;; ## Types

(defn ^:private rewrite-type
  "Rewrites the :type tag of a field (or an argument) into three keys: :type (just the keyword),
  :non-nullable? and :multiple?."
  [field]
  ;; TODO: This doesn't handle some variations, since either/both of
  ;; the list and the elements of the list may be non-nullable.
  ;; This applies non-nullable? only to the elements of a list, not to the list
  ;; itself.

  (loop [result (assoc field :multiple? false :non-nullable? false)
         term (:type field)]
    (cond-let

      (not (list? term))
      (assoc result :type (as-keyword term))

      :let [[k more] term]

      (= 'non-null k)
      (recur (assoc result :non-nullable? true) more)

      (= 'list k)
      (recur (assoc result :multiple? true) more)

      :else
      (throw (ex-info "Could not identify type of field."
                      {:field field
                       :unexpected-modifier k})))))

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

;;-------------------------------------------------------------------------------
;; ## Enforcers
;;
;; enforcers are just functions that are passed a tuple (a vector of resolved value and errors) and return
;; the same tuple, or a different one (or nil).

(defmacro ^:private with-resolved-value
  [binding & body]
  (let [[k tuple] binding]
    `(let [~k (first ~tuple)]
       (if (some? ~k)
         (do ~@body)
         ~tuple))))

(defn ^:private enforce-single-result
  [tuple]
  (with-resolved-value [value tuple]
    (if-not (sequential? value)
      tuple
      (error "Field resolver returned a collection of values, expected only a single value."))))

(defn ^:private enforce-multiple-results
  [tuple]
  (with-resolved-value [value tuple]
    (if (sequential? value)
      tuple
      (error "Field resolver returned a single value, expected a collection of values."))))

(def ^:private noop-enforcer identity)

(def ^:private resolved-nil [nil nil])

(defn ^:private compose-enforcers
  "enforcers may be nil, and are ordered outermost (executing last)
  to innermost (executing first), as with clojure.core/comp.
z
  Each composed enforcer may return a tuple with errors.
  Errors short-circuit the composed enforcers sequence."
  [& enforcers]
  (let [enforcers' (filterv some? (reverse enforcers))]
    (if
      (empty? enforcers')
      noop-enforcer

      (fn [resolved-tuple]
        (loop [this-tuple resolved-tuple
               [enforcer & remaining-enforcers] enforcers']
          (cond-let
            (nil? enforcer)
            this-tuple

            ;; Pass the prior value through the enforcer.
            :let [next-tuple (enforcer this-tuple)]

            ;; Terminate early when we hit any errors
            (-> next-tuple second some?)
            next-tuple

            :else
            (recur next-tuple remaining-enforcers)))))))

(defn ^:private map-enforcer
  "May wrap a single-value enforcer such that it maps across a collection of values.

  The result is a tuple of the collection of values, and the collection of errors."
  [enforcer]
  {:pre [(some? enforcer)]}
  (fn [tuple]
    ;;Go from a tuple of a sequence of values to a sequence of tuples
    (let [enforced-tuples (mapv #(enforcer [%])
                                (first tuple))
          resolved-values (mapv first enforced-tuples)
          errors  (->> enforced-tuples
                       (into [] (comp (keep second)
                                      (mapcat ensure-seq)))
                       seq)]
      [resolved-values errors])))

(defn ^:private coercion-enforcer
  [coercion-f]
  (fn [tuple]
    (with-resolved-value [value tuple]
      (-> value coercion-f vector))))

(defn ^:private reject-nil-enforcer
  [tuple]
  (let [value (first tuple)]
    (if (some? value)
      tuple
      (error "Non-nullable field was null."))))

(defn ^:private auto-apply-type-enforcer
  [type]
  (fn [tuple]
    (with-resolved-value [value tuple]
      (-> value (tag-with-type type) vector))))

(defn ^:private enforce-allowed-types
  [field-type allowed-types]
  (fn [tuple]
    (with-resolved-value [value tuple]
      (let [actual-type (type-tag value)]
        (if (contains? allowed-types actual-type)
          tuple
          (error "Value returned from resolver has incorrect type for field."
                 {:field-type field-type
                  :actual-type actual-type
                  :allowed-types allowed-types}))))))

(defn ^:private enforce-type-tag
  [tuple]
  (with-resolved-value [value tuple]
    (if (type-tag value)
      tuple
      (error "Field resolver returned an instance not tagged with a schema type."))))

(defn ^:private assert-and-wrap-error
  "An error returned by a resolver should be nil, a map, or a collection
  of maps. These maps should contain a :message key, but may contain others.
  Wrap them in a vector if necessary.

  Returns nil, or a collection of one or more valid error maps."
  [error-map-or-maps]
  (cond
    (nil? error-map-or-maps)
    nil

    (and (sequential? error-map-or-maps)
         (every? (comp string? :message)
                 error-map-or-maps))
    error-map-or-maps

    (string? (:message error-map-or-maps))
    [error-map-or-maps]

    :else
    (throw (ex-info (str "Errors must be nil, a map, or a sequence of maps "
                         "each containing, at minimum, a :message key.")
                    {:error error-map-or-maps}))))

(defn ^:private wrap-resolve-with-enforcer
  [resolve enforcer]
  (fn [context args value]
    ;; The resolve may return a ResolverResult; if not the first enforcer will
    ;; convert it to one.
    (let [raw-value (resolve context args value)
          ;; This is a little bit of optimization; satisfies? can
          ;; be a bit expensive.
          is-tuple? (when raw-value
                      (or (instance? ResolverResultImpl raw-value)
                          (satisfies? ResolverResult raw-value)))
          enforce-value (if is-tuple?
                          (resolved-value raw-value)
                          raw-value)
          resolver-errors (when is-tuple?
                            (resolve-errors raw-value))
          enforced-tuple (enforcer [enforce-value])
          all-errors (->> (sequence (comp cat
                                          (filter some?)
                                          (mapcat assert-and-wrap-error))
                           [(ensure-seq resolver-errors)
                            (ensure-seq (second enforced-tuple))]))]
      (resolve-as (first enforced-tuple) all-errors))))

(defn ^:private prepare-resolver
  "Prepares a field resolver to add type enforcement to a field.

  The new field resolver uniformly returns a tuple of the resolved value (or seq of values, if
  the field type is a list) and a seq of error maps."
  [schema field resolver]
  (let [{:keys [type multiple? non-nullable?]} field

        ;; :type is the name of the field's type.
        ;; field-type is the actual type map
        {:keys [category] :as field-type} (get schema type)

        coercion-f (when (= :scalar category)
                     (let [serializer (:serialize field-type)]
                       (fn [x]
                         (let [result (s/conform serializer x)]
                           (if-not (= result :clojure.spec/invalid)
                             result
                             (throw (ex-info "Invalid value for a scalar type."
                                             {:type type
                                              :value x})))))))

        coerce (when coercion-f
                 (coercion-enforcer coercion-f))

        reject-nil (when non-nullable?
                     reject-nil-enforcer)

        auto-apply-tag (when (#{:object :input-object} category)
                         (auto-apply-type-enforcer type))

        enforce-resolve-type (when (#{:interface :union} category)
                               (compose-enforcers
                                 (enforce-allowed-types type (:members field-type))
                                 enforce-type-tag))

        per-value-enforcers (compose-enforcers
                              enforce-resolve-type
                              auto-apply-tag
                              coerce
                              reject-nil)

        enforcer (compose-enforcers
                   (if multiple?
                     (map-enforcer per-value-enforcers)
                     per-value-enforcers)

                   (if multiple?
                     enforce-multiple-results
                     enforce-single-result))]
    (wrap-resolve-with-enforcer resolver enforcer)))

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
  (let [values (->> enum :values (mapv name))
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
    (doseq [[field-name field] (:fields input-object')
            :let [field-type (:type field)
                  type (get schema field-type)
                  category (:category type)]]
      (when-not type
        (throw (ex-info (format "Field %s of input object %s references unknown type %s."
                                (q field-name)
                                (-> input-object :type-name q)
                                (q field-type))
                        {:input-object input-object'
                         :field-name field-name
                         :schema-types (type-map schema)})))

      ;; Per 3.1.6, each field of an input object must be either a scalar, an enum,
      ;; or an input object.
      (when-not (#{:input-object :scalar :enum} category)
        (throw (ex-info (format "Field %s of input object %s must be type scalar, enum, or input-object."
                                (q field-name)
                                (-> input-object :type-name q))
                        {:input-object input-object'
                         :field-name field-name
                         :type type}))))
    input-object'))

(defmethod compile-type :interface
  [interface schema]
  (compile-fields interface))

(defn ^:private verify-fields-and-args
  "Verifies that the type of every field and every field argument is valid."
  [schema type]
  (doseq [[field-name field] (:fields type)]
    (when-not (get schema (:type field))
      (throw (ex-info (format "Field %s of type %s references unknown type %s."
                              (q field-name)
                              (-> type :type-name q)
                              (-> field :type q))
                      {:field field
                       :field-name field-name
                       :schema-types (type-map schema)})))

    (when (and (:non-nullable? field)
               (some? (:default-value field)))
      (throw (ex-info (format "Field %s of type %s is both non-nullable and has a default value."
                              (q field-name)
                              (-> type :type-name q))
                      {:field-name field-name
                       :field field})))

    (doseq [[arg-name arg] (:args field)
            :let [type (get schema (:type arg))]]
      (when-not type
        (throw (ex-info (format "Argument %s of field %s in type %s references unknown type %s."
                                (q arg-name)
                                (q field-name)
                                (-> type :type-name q)
                                (-> arg :type q))
                        {:field-name field-name
                         :arg-name arg-name
                         :field field})))

      (when-not (#{:scalar :enum :input-object} (:category type))
        (throw (ex-info (format "Argument %s of field %s in type %s is not a valid argument type."
                                (q arg-name)
                                (q field-name)
                                (-> type :type-name q))
                        {:field-name field-name
                         :arg-name arg-name
                         :field field}))))))

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

  (let [fields' (reduce-kv (fn [m field-name field]
                             (let [resolver (or (:resolve field)
                                                ((-> options :default-field-resolver) field-name))]
                               (assoc m field-name
                                      (assoc field :resolve
                                             (prepare-resolver schema field resolver)))))
                           {}
                           (:fields object))]
    (assoc object :fields fields')))

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

(s/def ::compile-options (s/keys :opt-un [::default-field-resolver]))


(def ^:private default-compile-opts
  {:default-field-resolver
   (fn [field-name]
     (let [hyphenized-field (-> field-name
                                name
                                (str/replace "_" "-")
                                keyword)]
       (fn [_ _ v]
         (get v hyphenized-field))))})

(defn compile
  "Compiles a schema, verifies its correctness, and inlines all types.

  Compile options:

  :default-field-resolver

  : A function that accepts a field name (as a keyword) and converts it into the
    default field resolver. The default implementation translates
    underscores in the name to dashes to form a keyword key.

  Produces a form ready to be used in executing a query."
  ([schema]
   (compile schema {}))
  ([schema options]
   (let [options' (merge default-compile-opts options)
         ;; TODO: Check if changing the default field resolver will break
         ;; the introspection schema.
         introspection-schema (introspection/introspection-schema)]
     (-> schema
         (deep-merge introspection-schema)
         (construct-compiled-schema options')))))

(s/fdef compile
        :args (s/cat :schema ::schema-object
                     :options (s/? ::compile-options)))
