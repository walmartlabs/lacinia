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
    [com.walmartlabs.lacinia.introspection :as introspection]
    [com.walmartlabs.lacinia.internal-utils
     :refer [map-vals map-kvs filter-vals deep-merge q
             is-internal-type-name? sequential-or-set? as-keyword
             cond-let ->TaggedValue is-tagged-value? extract-value extract-type-tag
             to-message qualified-name aggregate-results]]
    [com.walmartlabs.lacinia.resolve :as resolve
     :refer [ResolverResult resolve-as is-resolver-result?]]
    [clojure.string :as str]
    [clojure.set :refer [difference]]
    [clojure.pprint :as pprint]
    [com.walmartlabs.lacinia.selection :as selection]
    [com.walmartlabs.lacinia.selector-context :as sc])
  (:import
    (clojure.lang IObj)
    (java.io Writer)))

;; When using Clojure 1.8, the dependency on clojure-future-spec must be included,
;; and this code will trigger
(when (-> *clojure-version* :minor (< 9))
  (require '[clojure.future :refer [any? simple-keyword? simple-symbol?]]))

(defrecord CompiledSchema [])

(defn ^:no-doc compiled-schema?
  [m]
  (instance? CompiledSchema m))

;;-------------------------------------------------------------------------------
;; ## Helpers

(def ^:private graphql-identifier #"(?ix) _* [a-z] [a-z0-9_]*")

(defrecord ^:private CoercionFailure
  [message])

(defn coercion-failure
  "Returns a special record that indicates a failure coercing a scalar value.
  This may be returned from a scalar's :parse or :serialize callback.

  This is deprecated in version 0.32.0; just throw an exception instead.

  A coercion failure includes a message key, and may also include additional data.

  message
  : A message string presentable to a user.

  data
  : An optional map of additional details about the failure."
  {:added "0.16.0"
   :deprecated "0.32.0"}
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
       (filter :type-name)                                  ;; ::schema/root has no :type-name
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
  will return :clojure.spec/invalid or a [[coercion-failure]].

  This function has been deprecated, as Scalar parse and serialize callbacks are now
  simple functions, and not conformers."
  {:deprecated "0.31.0"}
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

(defn ^:private parse-int
  [v]
  ;; The serialized is a little more forgiving about converting non-integers to integers.
  ;; On the parse side, we're a little more picky.
  (when (integer? v)
    (if (<= Integer/MIN_VALUE v Integer/MAX_VALUE)
      (int v)
      (coercion-failure "Int value outside of allowed 32 bit integer range." {:value (pr-str v)}))))

(defn ^:private serialize-int
  [v]
  (cond
    (integer? v)
    (if (<= Integer/MIN_VALUE v Integer/MAX_VALUE)
      (int v)
      (coercion-failure "Int value outside of allowed 32 bit integer range." {:value (pr-str v)}))

    ;; Per spec; floats are allowed only if they are a whole number.
    (float? v)
    (when (= v (Math/floor (double v)))
      (let [long-v (long v)]
        (if (<= Integer/MIN_VALUE long-v Integer/MAX_VALUE)
          (int long-v)
          (coercion-failure "Int value outside of allowed 32 bit integer range." {:value (pr-str v)}))))))

(defn ^:private parse-float
  [v]
  (cond
    (instance? Double v)
    v

    ;; Spec: should coerce non-floating-point raw values as result coercion and for input coercion
    (number? v)
    (double v)))

(defn ^:private seralize-float
  [v]
  (cond
    (instance? Double v)
    v

    (number? v)
    (double v)

    (string? v)
    (try
      ;; Per the spec, if a string can be parsed to a double, that's allowed
      (Double/parseDouble v)
      (catch Throwable _
        nil))))

(defn ^:private parse-boolean
  [v]
  (when
    (instance? Boolean v)
    v))

(defn ^:private parse-id
  [v]
  (cond
    (string? v)
    v

    (integer? v)
    (str v)))

(defn ^:private serialize-id
  [v]
  ;; Although the spec discusses serializing the ID type "as appropriate", that would be a case
  ;; of overriding this default implementation.
  (when (string? v)
    v))

(defn ^:private parse-string
  [v]
  (when (string? v)
    v))

(def default-scalar-transformers
  {:String {:parse parse-string
            :serialize str}
   :Float {:parse parse-float
           :serialize seralize-float}
   :Int {:parse parse-int
         :serialize serialize-int}
   :Boolean {:parse parse-boolean
             :serialize parse-boolean}
   :ID {:parse parse-id
        :serialize serialize-id}})

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
;; The convention is that built-in types use a symbol, and application-defined types
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
                              ::directives
                              ::default-value]))
(s/def ::default-value any?)
(s/def ::args (s/map-of ::schema-key ::arg))
;; Defining these callbacks in spec has been a challenge. At some point,
;; we can expand this to capture a bit more about what a field resolver
;; is passed and should return.
(s/def ::resolve (s/or :function ::function-or-var
                       :protocol ::resolver-type))
(s/def ::resolver-type #(satisfies? resolve/FieldResolver %))
(s/def ::field (s/keys :opt-un [::description
                                ::resolve
                                ::args
                                ::directives
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
(s/def ::directives (s/coll-of ::directive))
(s/def ::directive (s/keys :req-un [::directive-type]
                           :opt-un [::directive-args]))
(s/def ::directive-type ::schema-key)
(s/def ::directive-args (s/map-of keyword? any?))
(s/def ::tag (s/or
               :symbol symbol?
               :class class?))
(s/def ::object (s/keys :req-un [::fields]
                        :opt-un [::implements
                                 ::directives
                                 ::description
                                 ::tag]))
;; Here we'd prefer a version of ::fields where :resolve was not defined.
(s/def ::interface (s/keys :opt-un [::description
                                    ::directives
                                    ::fields]))
;; A list of keyword identifying objects that are part of a union.
(s/def ::members (s/and (s/coll-of ::type-name)
                        seq))
(s/def ::union (s/keys :opt-un [::description
                                ::directives]
                       :req-un [::members]))
(s/def ::enum-value (s/and (s/nonconforming
                             (s/or :string string?
                                   :keyword simple-keyword?
                                   :symbol simple-symbol?))
                           ::graphql-identifier))
(s/def ::enum-value-def (s/or :bare-value ::enum-value
                              :described (s/keys :req-un [::enum-value]
                                                 :opt-un [::description
                                                          ::deprecated
                                                          ::directives])))
(s/def ::values (s/and (s/coll-of ::enum-value-def) seq))
;; Regrettably, :parse and :serialize on ::enum could reasonably be maps, but
;; that can't be easily expressed here (unless we create a :enum/parse and :enum/serialize).
;; We'll go there if there's a need for it.
(s/def ::enum (s/keys :opt-un [::description
                               ::parse
                               ::serialize
                               ::directives]
                      :req-un [::values]))
;; The type of an input object field is more constrained than an ordinary field, but that is
;; handled with compile-time checks.  Input objects should not have a :resolve or :args as well.
;; Defining an input-object in terms of :properties (with a corresponding ::properties and ::property spec)
;; may be more correct, but it's a big change.
(s/def ::input-object (s/keys :opt-un [::description
                                       ::directives]
                              :req-un [::fields]))
;; Prior to 0.31.0, specs were conformers.
;; With the breaking change in 0.31.0, we want to make sure that custom scalars
;; have been updated.
(s/def ::not-a-conformer #(not (s/spec? %)))
(s/def ::parse-or-serialize-fn (s/and ::not-a-conformer
                                      ::function-or-var))
(s/def ::function-or-var (s/or :function fn?
                               :var var?))
(s/def ::parse ::parse-or-serialize-fn)
(s/def ::serialize ::parse-or-serialize-fn)
(s/def ::scalar (s/keys :opt-un [::description
                                 ::directives]
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
(s/def ::stream ::function-or-var)

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
                   ;; Schema-level directives
                   ::directives
                   ::directive-defs]))

;; Again, this can be fleshed out once we have a handle on defining specs for
;; functions:
(s/def ::default-field-resolver ::function-or-var)

(s/def ::promote-nils-to-empty-list? boolean?)

(s/def ::enable-introspection? boolean?)

(s/def ::apply-field-directives fn?)

(s/def ::compile-options (s/keys :opt-un [::default-field-resolver
                                          ::promote-nils-to-empty-list?
                                          ::enable-introspection?
                                          ::apply-field-directives]))

(defn ^:private wrap-map
  [compiled-schema m]
  (map-vals #(assoc % :compiled-schema compiled-schema) m))

(defn ^:private wrap-list
  [compiled-schema coll]
  (mapv #(assoc % :compiled-schema compiled-schema) coll))

(defn select-type
  "Given a compiled schema and a keyword type name, returns a [[TypeDef]], or nil if not found."
  {:added "0.39"}
  [compiled-schema type-name]
  (let [type (get compiled-schema type-name)]
    (when (and (some? type)
               (satisfies? selection/TypeDef type))
      ;; Essentially, a bucket-brigade approach to passing the compiled schema along, so that
      ;; at field and argument definitions it is possible to jump to the selection/TypeDef of the element.
      (assoc type :compiled-schema compiled-schema))))

(defrecord ^:private Directive [directive-type arguments effector arguments-extractor]

  selection/Directive

  (directive-type [_] directive-type)

  selection/Arguments

  (arguments [_] arguments))

(defrecord ^:private Type [category type-name description fields directives compiled-directives
                           implements tag compiled-schema]

  selection/TypeDef

  (type-name [_] type-name)

  (type-category [_] :object)

  selection/Fields

  (fields [_] (wrap-map compiled-schema fields))

  selection/Directives

  (directives [_] compiled-directives))

(defn ^:no-doc root-type-name
  "For a compiled field (or argument) definition, delves down through the :type tag to find
  the root type name, a keyword."
  [element]
  ;; In some error scenarios, the query references an unknown field and
  ;; the field-def is nil. Without this check, this loops endlessly.
  (when element
    (loop [type-def (:type element)]
      (if (-> type-def :kind (= :root))
        (:type type-def)
        (recur (:type type-def))))))

(defrecord ^:private FieldDef [type type-string directives compiled-directives compiled-schema
                               field-name qualified-name args null-collapser]

  selection/FieldDef

  (field-name [_] field-name)

  selection/ArgumentDefs

  (argument-defs [_]
    (wrap-map compiled-schema args))

  selection/Type

  (kind [_]
    (assoc type :compiled-schema compiled-schema))

  (root-type [this]
    (select-type compiled-schema (root-type-name this)))

  (root-type-name [this] (root-type-name this))

  selection/QualifiedName

  (qualified-name [_] qualified-name)

  selection/Directives

  (directives [_] compiled-directives))

(defrecord ^:private Interface [category type-name member fields directives compiled-directives
                                compiled-schema]

  selection/TypeDef

  (type-name [_] type-name)

  (type-category [_] :interface)

  selection/Fields

  (fields [_] (wrap-map compiled-schema fields))

  selection/Directives

  (directives [_] compiled-directives))

(defrecord ^:private Union [category type-name description directives compiled-directives]

  selection/TypeDef

  (type-name [_] type-name)

  (type-category [_] :union)

  selection/Directives

  (directives [_] compiled-directives))

(defrecord ^:private EnumType [category type-name description parse serialize values
                               values-detail values-set
                               directives compiled-directives]

  selection/TypeDef

  (type-name [_] type-name)

  (type-category [_] :enum)

  selection/Directives

  (directives [_] compiled-directives))

(defrecord ^:private Scalar [category type-name description parse serialize directives compiled-directives]

  selection/TypeDef

  (type-name [_] type-name)

  (type-category [_] :scalar)

  selection/Directives

  (directives [_] compiled-directives))

(defn ^:private compile-directives
  [element]
  (let [{:keys [directives]} element]
    (if (seq directives)
      (assoc element :compiled-directives (->> directives
                                               (map (fn [{:keys [directive-type directive-args]}]
                                                      (map->Directive
                                                        {:directive-type directive-type
                                                         :arguments directive-args})))
                                               (group-by selection/directive-type)))
      element)))

(defn ^:private apply-directive-arg-defaults
  "Called, late during compilation, to inject default values for
  directive arguments into the directives on the element."
  [schema element]
  (if-not (:compiled-directives element)
    element
    (let [{:keys [::directive-defs]} schema
          f (fn [compiled-directive]
              (let [{:keys [directive-type arguments]} compiled-directive
                    directive-def (get directive-defs directive-type)
                    apply-defaults (fn [m k]
                        (let [default-value (get-in directive-def [:args k :default-value])]
                          (if (and (some? default-value)
                                   (nil? (get m k)))
                            (assoc m k default-value)
                            m)))
                     arguments' (reduce apply-defaults arguments (-> directive-def :args keys))]
                (assoc compiled-directive :arguments arguments')))
          g (fn [directives]
              (mapv f directives))]
      (update element :compiled-directives
              #(map-vals g %)))))

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

(defn ^:private type->string
  "Converts the result of expand-type back into a string, as a type reference would appear in the
  query language or SDL (e.g., `[String]!`)."
  [input-type]
  (let [{:keys [kind type]} input-type]
    (case kind
      :root (name type)
      :list (str "[" (type->string type) "]")
      :non-null (str (type->string type) "!"))))

(defrecord ^:private Kind [compiled-schema kind type]

  ;; For historical reasons, the naming here is all over the place.
  ;; kind is one of :root, :non-null, or :list
  ;; type is either another Kind, or the name of a TypeDef

  selection/Kind

  (kind-type [_] kind)

  (as-type-string [this] (type->string this))

  (of-kind [_]
    (when-not (= :root kind)
      (assoc type :compiled-schema compiled-schema)))

  (of-type [_]
    (when (= :root kind)
      (select-type compiled-schema type))))

(defn ^:no-doc expand-type
  "Compiles a type from the input schema to the format used in the
  compiled schema."
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

      (map->Kind {:kind kind
                  :type (expand-type next-type)}))

    ;; By convention, symbols are used for pre-defined scalar types, and
    ;; keywords are used for user-defined types, interfaces, unions, enums, etc.
    (or (keyword? type)
        (symbol? type))
    (map->Kind {:kind :root
                :type (as-keyword type)})

    :else
    (throw (ex-info "Could not process type."
                    {:type type}))))

(defn ^:private add-type-string
  [field-definition]
  (let [field-type (:type field-definition)
        type-string (type->string field-type)]
    (assoc field-definition :type-string type-string)))

(defn ^:private rewrite-type
  "Rewrites the type tag of a field (or argument) into a nested structure of types.

  types are maps with two keys, :kind and :type.

  :kind may be :list, :non-null, or :root.

  :type is a nested type map, or (for :root kind), the keyword name of a
  schema type (a scalar, enum, object, etc.)."
  [element]
  (try
    (update element :type expand-type)
    (catch Throwable t
      (throw (ex-info "Could not identify type of element (field or argument)."
                      {:element element}
                      t)))))

(defrecord ArgumentDef [arg-name compiled-schema type qualified-name
                        description directives default-value has-default-value? is-required?]

  selection/ArgumentDef

  selection/QualifiedName

  (qualified-name [_] qualified-name)

  selection/Type

  (kind [_]
    (assoc type :compiled-schema compiled-schema))

  (root-type [this]
    (select-type compiled-schema (root-type-name this)))

  (root-type-name [this] (root-type-name this)))

(defn ^:private compile-arg
  [arg-name arg-def]
  (let [arg-def' (-> (rewrite-type arg-def) map->ArgumentDef)
        has-default-value? (contains? arg-def :default-value)
        is-required? (and (= :non-null (get-in arg-def' [:type :kind]))
                          (not has-default-value?))]
    (assoc arg-def'
           :arg-name arg-name
           ;; Older code used (contains? arg :default-value) but that doesn't work anymore
           ;; with a record that has a default-value field, so ... even more fields.
           :has-default-value? has-default-value?
           :is-required? is-required?)))

(defn ^:private is-null?
  [v]
  (= v ::null))

(defn ^:private null-to-nil
  [v]
  (if (is-null? v) nil v))

(defn ^:private collapse-nulls-in-object
  [forgive-null? map-type? value]
  (cond
    (nil? value)
    value

    (is-null? value)
    (if forgive-null?
      nil
      ::null)

    (not map-type?)
    value

    (some is-null? (vals value))
    (if forgive-null? nil ::null)

    :else
    (map-vals null-to-nil value)))

(defn ^:no-doc collapse-nulls-in-map
  [m]
  (collapse-nulls-in-object true true m))

(defn ^:private build-null-collapser
  "Builds a null-collapser for a field definition; the null collapser transforms a resolved value
  for the field, potentially to the value ::null if it is nil but non-nullable OR if any sub-selection
  collapses to ::null.

  A nullable field that contains a value of ::null collapses to nil.

  For lists, a list that contains a ::null collapses down to either nil or ::null."
  [schema forgive-null? type]
  (let [{:keys [kind]
         nested-type :type} type]
    (case kind
      :root
      (let [element-def (get schema nested-type)
            {:keys [category]} element-def
            map-type? (contains? #{:union :object :interface} category)]
        (fn [value]
          (collapse-nulls-in-object forgive-null? map-type? value)))

      :non-null
      (let [nested-collapser (build-null-collapser schema false nested-type)]
        (fn [value]
          (let [value' (nested-collapser value)]
            (if (nil? value')
              ::null
              value'))))

      :list
      (let [nested-collapser (build-null-collapser schema true nested-type)
            promote-nils-to-empty-list (get-in schema [::options :promote-nils-to-empty-list?])
            empty-list (if promote-nils-to-empty-list [] nil)]
        (fn [values]
          (let [values' (when values
                          (map nested-collapser values))]
            (cond
              (nil? values')
              empty-list

              (some is-null? values')
              (if forgive-null? empty-list ::null)

              :else
              values')))))))

(defn ^:private compile-field
  "Rewrites the type of the field, and the type of any arguments."
  [schema type-def field-name field-def]
  (let [{:keys [type-name]} type-def
        field-def' (-> field-def
                       map->FieldDef
                       rewrite-type
                       add-type-string
                       compile-directives
                       (assoc :field-name field-name
                              :qualified-name (qualified-name type-name field-name))
                       (update :args #(map-kvs (fn [arg-name arg-def]
                                                 [arg-name (assoc (compile-arg arg-name arg-def)
                                                                  :qualified-name (qualified-name type-name field-name arg-name))])
                                               %)))
        collapser (build-null-collapser schema true (:type field-def'))]
    (assoc field-def' :null-collapser collapser)))

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
        (cond-> error (update :errors conj error))
        callback)))

(defn ^:private create-root-selector
  "Creates a selector function for the :root kind, which is the point at which
  a type refers to something in the schema.

  type - object definition containing the field
  field - field definition
  field-type-name - from the root :root kind "
  [schema field-def field-type-name]
  (let [field-type (get schema field-type-name)
        _ (when (nil? field-type)
            (throw (ex-info (format "Field %s references unknown type %s."
                                    (-> field-def :qualified-name q)
                                    (-> field-def :type q))
                            {:field field-def
                             :schema-types (type-map schema)})))
        category (:category field-type)

        ;; Build up layers of checks and other logic and a series of chained selector functions.
        ;; Normally, don't redefine local symbols, but here it makes it easier to follow and less
        ;; brittle.

        selector floor-selector

        selector (if (= :scalar category)
                   (let [serializer (:serialize field-type)]
                     (fn select-coercion [selector-context]
                       (cond-let

                         :let [{:keys [resolved-value]} selector-context]

                         (nil? resolved-value)
                         (selector selector-context)

                         :let [serialized (try
                                            (serializer resolved-value)
                                            (catch Throwable t
                                              (coercion-failure (to-message t) (ex-data t))))]

                         (nil? serialized)
                         (selector-error selector-context
                                         (let [value-str (pr-str resolved-value)]
                                           {:message (format "Unable to serialize %s as type %s."
                                                             value-str
                                                             (q field-type-name))
                                            :value value-str
                                            :type-name field-type-name}))

                         (is-coercion-failure? serialized)
                         (selector-error selector-context
                                         (-> serialized
                                             (update :message
                                                     #(str "Coercion error serializing value: " %))
                                             (assoc :type-name field-type-name
                                                    :value (pr-str resolved-value))))

                         :else
                         (selector (assoc selector-context :resolved-value serialized)))))
                   selector)

        selector (if (= :enum category)
                   (let [possible-values (-> field-type :values set)
                         serializer (:serialize field-type)]
                     (fn validate-enum [{:keys [resolved-value]
                                         :as selector-context}]
                       (cond-let
                         ;; The resolver function can return a value that makes sense from
                         ;; the application's model (for example, a namespaced keyword or even a string)
                         ;; and the enum's serializer converts that to a keyword, which is then
                         ;; validated to match a known value for the enum.

                         (nil? resolved-value)
                         (selector selector-context)

                         :let [serialized (serializer resolved-value)]

                         (not (possible-values serialized))
                         (selector-error selector-context (error "Field resolver returned an undefined enum value."
                                                                 {:resolved-value resolved-value
                                                                  :serialized-value serialized
                                                                  :enum-values possible-values}))

                         :else
                         (selector (assoc selector-context :resolved-value serialized)))))
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
          (nil? resolved-value)
          (callback (assoc selector-context
                           :resolved-value nil
                           :resolved-type nil))

          (not (sequential-or-set? resolved-value))
          (selector-error selector-context
                          (error "Field resolver returned a single value, expected a collection of values."))

          ;; Optimization for empty seqs:
          (not (seq resolved-value))
          (callback (assoc selector-context
                           :resolved-value []
                           :resolved-type nil))

          :else
          ;; So we have some privileged knowledge here: the callback returns a ResolverResult containing
          ;; the value. So we need to combine those together into a new ResolverResult.
          (let [unwrapper (fn [{:keys [resolved-value] :as selector-context}]
                            (if-not (sc/is-wrapped-value? resolved-value)
                              (next-selector selector-context)
                              (loop [v resolved-value
                                     sc selector-context]
                                (let [next-v (:value v)
                                      next-sc (sc/apply-wrapped-value sc v)]
                                  (if (sc/is-wrapped-value? next-v)
                                    (recur next-v next-sc)
                                    (next-selector (assoc next-sc :resolved-value next-v)))))))]
            (aggregate-results
              (map-indexed
                (fn [i v]
                  (unwrapper (-> selector-context
                                 (assoc :resolved-value v)
                                 (update-in [:execution-context :path] conj i))))
                resolved-value))))))

    :non-null
    (let [next-selector (assemble-selector schema object-type field (:type type))]
      (when (-> field :default-value some?)
        (throw (ex-info (format "Field %s is both non-nullable and has a default value."
                                (-> field :qualified-name q))
                        {:field-name (:qualified-name field)
                         :type (-> field :type type->string)})))
      (fn select-non-null [{:keys [resolved-value]
                            :as selector-context}]
        (if (some? resolved-value)
          (next-selector selector-context)
          (selector-error selector-context
                          ;; If there's already errors (from the application's resolver function) then don't add more
                          (when-not (-> selector-context :errors seq)
                            (error "Non-nullable field was null."))))))

    :root                                                   ;;
    (create-root-selector schema field (:type type))))

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
  "Prepares a field for execution. Provides a default resolver if necessary, optionally
  wraps that function to handle field directives, and the wraps the result to
  ensure it returns a ResolverResult.

  Inherits :documentation from matching inteface field as necessary.

  Adds a :selector function."
  [schema type-def field-def]
  (let [field-def' (apply-directive-arg-defaults schema field-def)
        {:keys [field-name description]} field-def'
        type-name (:type-name type-def)
        selector (assemble-selector schema type-def field-def' (:type field-def'))]
    (-> field-def'
        (assoc :type-name type-name
               :description (or description
                                (default-field-description schema type-def field-name))
               :selector selector)
        (provide-default-arg-descriptions schema type-def))))

(defn ^:private prepare-field-resolver
  [schema options field-def]
  (let [{:keys [default-field-resolver apply-field-directives]} options
        {:keys [field-name compiled-directives]} field-def
        resolver (or (:resolve field-def)
                     (default-field-resolver field-name))
        resolver' (if-not (and apply-field-directives
                               (seq compiled-directives))
                    resolver
                    (or (apply-field-directives (assoc field-def :compiled-schema schema) (resolve/as-resolver-fn resolver))
                        resolver))]
    (assoc field-def :resolve (wrap-resolver-to-ensure-resolver-result resolver'))))

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
  [schema type-def]
  (update type-def :fields #(map-kvs (fn [field-name field-def]
                                       [field-name (compile-field schema type-def field-name field-def)])
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

(defmethod compile-type :scalar
  [type schema]
  (-> type
      map->Scalar
      compile-directives))

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
    (-> union
        map->Union
        compile-directives
        (assoc :members members))))

(defn ^:private apply-deprecated-directive
  "For a field definition or enum value definition, checks for a :deprecated annotation and,
  if present, sets the definitions :deprecated key."
  [element-def]
  (if-let [directive (some->> element-def
                              :directives
                              (filter #(-> % :directive-type (= :deprecated)))
                              first)]
    (assoc element-def :deprecated (get-in directive [:directive-args :reason] true))
    element-def))

(defn ^:private normalize-enum-value-def
  "The :values key of an enum definition is either a seq of enum values, or a seq of enum value defs.
  The enum values are just the keyword/symbol/string.
  The enum value defs have keys :enum-value, :description, and :directives and optional keys
  :parse and :serialize.
  This normalizes into the enum value def form, and ensures that the :enum-value key is a keyword."
  [value-def]
  (if (map? value-def)
    (update value-def :enum-value as-keyword)
    {:enum-value (as-keyword value-def)}))

(defmethod compile-type :enum
  [enum-def _]
  (let [value-defs (->> enum-def
                        :values
                        (map normalize-enum-value-def)
                        (mapv apply-deprecated-directive))
        {:keys [serialize parse]
         :or {serialize as-keyword
              parse identity}} enum-def
        values (mapv :enum-value value-defs)
        values-set (set values)
        ;; The detail for each value is the map that may includes :enum-value and
        ;; may include :description, :deprecated, and/or :directives.
        details (reduce (fn [m {:keys [enum-value] :as detail}]
                          (assoc m enum-value detail))
                        {}
                        value-defs)]
    (when-not (= (count values) (count values-set))
      (throw (ex-info (format "Values defined for enum %s must be unique."
                              (-> enum-def :type-name q))
                      {:enum enum-def})))
    (-> enum-def
        map->EnumType
        compile-directives
        (assoc
          :parse parse
          :serialize serialize
          :values values
          :values-detail details
          :values-set values-set))))

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
    (let [object' (-> object
                      map->Type
                      (assoc :implements implements
                             :tag tag-class)
                      compile-directives)]
      (compile-fields schema object'))))

(defmethod compile-type :input-object
  [input-object schema]
  (let [input-object' (compile-fields schema input-object)]
    (doseq [field-def (-> input-object' :fields vals)
            :let [field-type-name (root-type-name field-def)
                  qualified-field-name (:qualified-name field-def)
                  type-def (get schema field-type-name)
                  category (:category type-def)]]
      (when-not type-def
        (throw (ex-info (format "Field %s references unknown type %s."
                                (q qualified-field-name)
                                (q field-type-name))
                        {:field-name qualified-field-name
                         :schema-types (type-map schema)}))))
    input-object'))

(defmethod compile-type :interface
  [interface schema]
  (->> interface
       map->Interface
       compile-directives
       (compile-fields schema)))

(defn ^:private extract-type-name
  "Navigates a type map down to the root kind and returns the type name."
  [type-map]
  (if (-> type-map :kind (= :root))
    (:type type-map)
    (recur (:type type-map))))

(defn ^:private unknown-directive
  [location element-def directive-type]
  (let [type-name (:type-name element-def)
        category (-> element-def :category name str/capitalize)]
    (throw (ex-info (format "%s %s references unknown directive @%s."
                            category
                            (q type-name)
                            (name directive-type))
                    {location type-name
                     :directive-type directive-type}))))

(defn ^:private inapplicable-directive
  [location element-def directive-def]
  (let [{:keys [directive-type locations]} directive-def
        ;; TODO: If we add :qualified-name to types as well, then we can use that here
        type-name (:type-name element-def)
        category (-> element-def :category name)]
    (throw (ex-info (format "Directive @%s on %s %s is not applicable."
                            (name directive-type)
                            category
                            (q type-name))
                    {location type-name
                     :directive-type directive-type
                     :allowed-locations locations}))))

(defn ^:private validate-directives-in-def
  [schema object-def location]
  (doseq [{:keys [directive-type]} (:directives object-def)
          :let [directive-def (get-in schema [::directive-defs directive-type])]]
    (when-not directive-def
      (unknown-directive location object-def directive-type))

    (when-not (-> directive-def :locations (contains? location))
      (inapplicable-directive location object-def directive-def))))

(defn ^:private verify-fields-and-args
  "Verifies that the type of every field and every field argument is valid."
  [schema object-def]
  (let [directive-defs (::directive-defs schema)
        input-object? (= :input-object (:category object-def))
        location (if input-object?
                   :input-field-definition
                   :field-definition)]
    (doseq [field-def (-> object-def :fields vals)
            :let [field-type-name (extract-type-name (:type field-def))
                  qualified-field-name (:qualified-name field-def)
                  field-type (get schema field-type-name)
                  field-category (:category field-type)]]
      (when (nil? field-type)
        (throw (ex-info (format "Field %s references unknown type %s."
                                (q qualified-field-name)
                                (q field-type-name))
                        {:field-name qualified-field-name
                         :schema-types (type-map schema)})))

      (when (and (not input-object?)
                 (= :input-object field-category))
        (throw (ex-info (format "Field %s is type %s, input objects may only be used as field arguments."
                                (q qualified-field-name)
                                (q field-type-name))
                        {:field-name qualified-field-name
                         :schema-types (type-map schema)})))

      (when (and input-object?
                 (not (#{:scalar :enum :input-object} field-category)))
        (throw (ex-info (format "Field %s is type %s, input objects may only contain fields that are scalar, enum, or input object."
                                (q qualified-field-name)
                                (q field-type-name))
                        {:field-name qualified-field-name
                         :schema-types (type-map schema)})))

      (doseq [{:keys [directive-type]} (:directives field-def)
              :let [directive (get directive-defs directive-type)]]
        (when-not directive
          (throw (ex-info (format "Field %s references unknown directive @%s."
                                  (q qualified-field-name)
                                  (name directive-type))
                          {:field-name qualified-field-name
                           :directive-type directive-type})))

        (when-not (-> directive :locations (contains? location))
          (throw (ex-info (format "Directive @%s on field %s is not applicable."
                                  (name directive-type)
                                  (q qualified-field-name))
                          {:field-name qualified-field-name
                           :directive-type directive-type
                           :allowed-locations (:locations directive)}))))

      (doseq [arg-def (-> field-def :args vals)
              :let [arg-type-name (extract-type-name (:type arg-def))
                    arg-type-def (get schema arg-type-name)
                    qualified-arg-name (:qualified-name arg-def)]]
        (when-not arg-type-def
          (throw (ex-info (format "Argument %s references unknown type %s."
                                  (q qualified-arg-name)
                                  (q arg-type-name))
                          {:arg-name qualified-arg-name
                           :schema-types (type-map schema)})))

        (when-not (#{:scalar :enum :input-object} (:category arg-type-def))
          (throw (ex-info (format "Argument %s is must be a scalar type, an enum, or an input object."
                                  (q qualified-arg-name))
                          {:arg-name qualified-arg-name})))

        (doseq [{:keys [directive-type]} (:directives arg-def)
                :let [directive (get directive-defs directive-type)]]
          (when-not directive
            (throw (ex-info (format "Argument %s references unknown directive @%s."
                                    (q qualified-arg-name)
                                    (name directive-type))
                            {:arg-name qualified-arg-name
                             :directive-type directive-type})))

          (when-not (-> directive :locations (contains? :argument-definition))
            (throw (ex-info (format "Directive @%s on argument %s is not applicable."
                                    (name directive-type)
                                    (q qualified-arg-name))
                            {:arg-name qualified-arg-name
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
                 (validate-directives-in-def schema interface :interface)
                 (let [interface-name (:type-name interface)
                       implementors (->> objects
                                         (filter #(-> % :implements interface-name))
                                         (map :type-name)
                                         set)
                       fields' (->> interface
                                    :fields
                                    (map-vals #(assoc % :type-name interface-name))
                                    (map-vals apply-deprecated-directive))]
                   (-> interface
                       (assoc :members implementors
                              :fields fields')
                       (dissoc :resolve)))))))

(defn ^:private prepare-and-validate-object
  [schema object-def]
  (verify-fields-and-args schema object-def)
  (let [object-def? (= :object (:category object-def))]
    (validate-directives-in-def schema object-def (if object-def? :object :input-object))
    (doseq [interface-name (:implements object-def)
            :let [interface (get schema interface-name)
                  type-name (:type-name object-def)]
            [field-name interface-field] (:fields interface)
            :let [object-field (get-in object-def [:fields field-name])
                  interface-field-args (:args interface-field)
                  object-field-args (:args object-field)]]

      (when-not object-field
        (throw (ex-info "Missing interface field in object definition."
                        {:object type-name
                         :field-name field-name
                         :interface-name interface-name})))

      (when-not (is-assignable? schema interface-field object-field)
        (throw (ex-info "Object field is not compatible with extended interface type."
                        {:interface-name interface-name
                         :field-name (:qualified-name object-field)})))

      (when interface-field-args
        (doseq [interface-field-arg interface-field-args
                :let [[arg-name interface-arg-def] interface-field-arg
                      object-field-arg-def (get object-field-args arg-name)]]

          (when-not object-field-arg-def
            (throw (ex-info "Missing interface field argument in object definition."
                            {:field-name (:qualified-name object-field)
                             :interface-argument (:qualified-name interface-arg-def)})))

          (when-not (is-assignable? schema interface-arg-def object-field-arg-def)
            (throw (ex-info "Object field's argument is not compatible with extended interface's argument type."
                            {:interface-name interface-name
                             :argument-name (:qualified-name object-field-arg-def)})))))

      (when-let [additional-args (seq (difference (into #{} (keys object-field-args))
                                                  (into #{} (keys interface-field-args))))]
        (doseq [additional-arg-name additional-args
                :let [arg-kind (get-in object-field-args [additional-arg-name :type :kind])]]
          (when (= arg-kind :non-null)
            (throw (ex-info "Additional arguments on an object field that are not defined in extended interface cannot be required."
                            {:interface-name interface-name
                             :argument-name (-> object-field-args (get additional-arg-name) :qualified-name)}))))))

    (-> (apply-directive-arg-defaults schema object-def)
        (update :fields #(map-vals (fn [field-def]
                                     (cond-> (prepare-field schema object-def field-def)
                                       object-def? apply-deprecated-directive))
                                   %)))))

(defn ^:private prepare-and-validate-objects
  "Comes very late in the compilation process to prepare objects, including validation that
  all implemented interface fields are present in each object."
  [schema category]
  (map-types schema category
             #(prepare-and-validate-object schema %)))

(defn ^:private prepare-resolvers-in-object
  [schema object-def options]
  (update object-def :fields #(map-vals (fn [field-def]
                                          (prepare-field-resolver schema options field-def))
                                        %)))

(defn ^:private prepare-field-resolvers
  [schema options]
  (map-types schema :object
             #(prepare-resolvers-in-object schema % options)))

(def ^:private default-subscription-resolver

  ^ResolverResult
  (fn [_ _ value]
    (resolve-as value)))

(defn ^:private add-root
  "Adds a root object for 'extra' operations (e.g., the :queries map in the input schema)."
  [compiled-schema object-name operation-key fields]
  (cond-let
    :let [existing (get compiled-schema object-name)]

    (nil? existing)
    (assoc compiled-schema object-name
           (map->Type {:category :object
                       :type-name object-name
                       :fields fields}))

    (empty? fields)
    compiled-schema

    ;; Ok, so here's the less fun case - the object is already in the schema and there are fields defiend on that object
    ;; there but there are also fields separately ... say, due to Introspection adding queries.
    :else
    (let [merged-fields (reduce-kv (fn [m k v]
                                     (when (contains? m k)
                                       (throw (ex-info (format "Name collision compiling schema: %s already exists with value from %s."
                                                               (q (qualified-name object-name k))
                                                               operation-key)
                                                       {:field-name k})))
                                     (assoc m k v))
                                   (:fields existing)
                                   fields)]
      (assoc-in compiled-schema [object-name :fields] merged-fields))))

(defn ^:private compile-directive-defs
  [schema directive-defs]
  (let [compile-directive-arg (fn [directive-type arg-name arg-def]
                                (let [arg-def' (compile-arg arg-name arg-def)
                                      arg-type-name (extract-type-name arg-def')
                                      arg-type (get schema arg-type-name)]
                                  (when-not arg-type
                                    (throw (ex-info "Unknown argument type."
                                                    {:arg-name arg-name
                                                     :arg-type-name arg-type-name
                                                     :schema-types (type-map schema)})))
                                  (when-not (#{:enum :scalar :input-object} (:category arg-type))
                                    (throw (ex-info "Directive argument is not a scalar, enum, or input object type."
                                                    {:arg-name arg-name
                                                     :arg-type-name arg-type-name
                                                     :schema-types (type-map schema)})))
                                  [arg-name (assoc arg-def'
                                                   :qualified-name (qualified-name nil directive-type arg-name))]))
        compile-directive-args (fn [directive-type directive-def]
                                 [directive-type (-> directive-def
                                                     (assoc :directive-type directive-type)
                                                     (update :args (fn [args]
                                                                     (map-kvs #(compile-directive-arg directive-type %1 %2) args))))])]
    (assoc schema ::directive-defs
           (map-kvs compile-directive-args
                    (assoc directive-defs
                           :deprecated {:args {:reason {:type 'String}}
                                        :locations #{:field-definition :enum-value}})))))

(defn ^:private validate-directives-by-category
  [schema category]
  (run!
    #(validate-directives-in-def schema % category)
    (types-with-category schema category))

  schema)

(defn ^:private validate-enum-directives
  [schema]
  (doseq [enum-def (types-with-category schema :enum)]
    (doseq [{:keys [directive-type]} (:directives enum-def)
            :let [directive-def (get-in schema [::directive-defs directive-type])]]
      (when-not directive-def
        (unknown-directive :enum enum-def directive-type))

      (when-not (contains? (:locations directive-def) :enum)
        (inapplicable-directive :enum enum-def directive-def)))

    (doseq [{:keys [enum-value directives]} (-> enum-def :values-detail vals)
            :let [value-name (keyword (-> enum-def :type-name name) (name enum-value))]
            {:keys [directive-type]} directives
            :let [{:keys [locations] :as directive-def} (get-in schema [::directive-defs directive-type])]]
      (when-not directive-def
        (throw (ex-info (format "Enum value %s referenced unknown directive @%s."
                                (q value-name)
                                (name directive-type))
                        {:enum-value value-name
                         :directive-type directive-type})))

      (when-not (contains? locations :enum-value)
        (throw (ex-info (format "Directive @%s on enum value %s is not applicable."
                                (name directive-type)
                                (q value-name))
                        {:enum-value value-name
                         :directive-type directive-type
                         :allowed-locations locations})))))
  schema)

(defn ^:private construct-compiled-schema
  [schema options]
  ;; Note: using merge, not two calls to xfer-types, since want to allow
  ;; for overrides of the built-in scalars without a name conflict exception.
  (let [merged-scalars (->> schema
                            :scalars
                            (merge default-scalar-transformers)
                            (map-vals #(assoc % :category :scalar)))
        {:keys [query mutation subscription]
         :or {query :Query
              mutation :Mutation
              subscription :Subscription}} (map-vals as-keyword (:roots schema))
        defaulted-subscriptions (->> schema
                                     :subscriptions
                                     (map-vals #(if-not (:resolve %)
                                                  (assoc % :resolve default-subscription-resolver)
                                                  %)))]
    (-> {::roots {:query query
                  :mutation mutation
                  :subscription subscription}
         ::options options}
        (xfer-types merged-scalars :scalar)
        (xfer-types (:enums schema) :enum)
        (xfer-types (:unions schema) :union)
        (xfer-types (:objects schema) :object)
        (xfer-types (:interfaces schema) :interface)
        (xfer-types (:input-objects schema) :input-object)
        (add-root query :queries (:queries schema))
        (add-root mutation :mutations (:mutations schema))
        (add-root subscription :subscriptions defaulted-subscriptions)
        (as-> s
              (map-vals #(compile-type % s) s))
        (compile-directive-defs (:directive-defs schema))
        (prepare-and-validate-interfaces)
        (prepare-and-validate-objects :object)
        (prepare-and-validate-objects :input-object)
        (validate-directives-by-category :union)
        (validate-directives-by-category :scalar)
        validate-enum-directives
        ;; Last so that schema is as close to final and verified state as possible
        (prepare-field-resolvers options)
        map->CompiledSchema)))

(defn default-field-resolver
  "The default for the :default-field-resolver option, this uses the field name as the key into
  the resolved value."
  [field-name]
  ^{:tag ResolverResult}
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
  {:default-field-resolver default-field-resolver
   :promote-nils-to-empty-list? false
   :enable-introspection? true})

(s/def ::compile-args
  (s/cat :schema ::schema-object
         :options (s/? (s/nilable ::compile-options))))

(defn compile
  "Compiles a schema, verifies its correctness, and prepares it for query execution.
  The compiled schema is in an entirely different format than the input schema.

  The format of the compiled schema is subject to change without notice.

  This function explicitly validates its arguments using clojure.spec.

  Compile options:

  :default-field-resolver

  : A function that accepts a field name (as a keyword) and converts it into the
    default field resolver; this defaults to [[default-field-resolver]].

  :promote-nils-to-empty-list?
  : Returns the prior, incorrect behavior, where a list field that resolved to nil
    would be \"promoted\" to an empty list. This may be necessary when existing clients
    rely on the incorrect behavior, which was fixed in 0.31.0.

  :enable-introspection?
  : If true (the default), then Schema introspection is enabled. Some applications
    may disable introspection in production.

  :apply-field-directives
  : An optional callback function; for fields that have any directives on the field definition,
    the callback is invoked; it is passed the [[FieldDef]] (from which directives may be extracted)
    and the base field resolver function (possibly, a default field resolver).
    The callback may return a new field resolver function, or return nil to use the base field resolver function.

    A [[FieldResolver]] instance is converted to a function before being passed to the callback.

    The callback should be aware that the base resolver function may return a raw value, or a [[ResolverResult]].
    Generally, this option is used with the [[wrap-resolver-result]] function.

    This processing occurs at the very end of schema compilation.

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
         {:keys [enable-introspection?]} options'
         introspection-schema (when enable-introspection?
                                (introspection/introspection-schema))]
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
