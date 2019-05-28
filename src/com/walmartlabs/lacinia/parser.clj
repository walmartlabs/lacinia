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

(ns com.walmartlabs.lacinia.parser
  "Parse a query document using a compiled schema.

  Also provides functions that operate on the parsed query."
  (:require
    [clojure.string :as str]
    [com.walmartlabs.lacinia.internal-utils
     :refer [cond-let update? q map-vals filter-vals remove-vals
             with-exception-context throw-exception to-message
             keepv as-keyword *exception-context*]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.constants :as constants]
    [clojure.spec.alpha :as s]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.lacinia.parser.query :as qp]
    [com.walmartlabs.lacinia.vendor.ordered.map :refer [ordered-map]])
  (:import
    (clojure.lang ExceptionInfo)))

(defn ^:private first-match
  [pred coll]
  (->> coll
       (filter pred)
       first))

(defn ^:private assoc-seq?
  "Associates a key into map only when the value is a non-empty seq."
  [m k v]
  (if (seq v)
    (assoc m k v)
    m))

(declare ^:private selection)

(defn ^:private contains-modifier?
  [modifier-kind any-def]
  (loop [{kind :kind
          nested :type} (:type any-def)]
    (cond
      (= :root kind)
      false

      (= kind modifier-kind)
      true

      :else
      (recur nested))))

(defn ^:private non-null-kind?
  "Peeks at the kind of the provided def (field, argument, or variable) to see if it is :non-null"
  [any-def]
  (-> any-def :type :kind (= :non-null)))

;; At some point, this will move to the schema when we work out how to do extensible
;; directives. A directive effector is invoked during the prepare phase to modify
;; a node based on the directive arguments.
(def ^:private builtin-directives
  (let [if-arg {:if {:type {:kind :non-null
                            :type {:kind :root
                                   :type :Boolean}}}}]
    {:skip {:args if-arg
            :effector (fn [node arguments]
                        (cond-> node
                          (-> arguments :if true?) (assoc :disabled? true)))}
     :include {:args if-arg
               :effector (fn [node arguments]
                           (cond-> node
                             (-> arguments :if false?) (assoc :disabled? true)))}}))

(declare ^:private build-map-from-parsed-arguments)

(defn ^:private xform-argument-value
  "Returns a tuple of type and string value.  True scalar values will be passed,
  as strings, to the scalar's :parse function to convert to the appropriate type
  (in some cases, this is more flexible than what the GraphQL spec mandates).

  Most values are returned with type :scalar.

  Other types:

  :enum is handled specially, as it is not a scalar.

  :null is handled specially, as it potentially can apply to any other type, including
  list types.

  :array and :object are composite types.

  :variable must be resolved later, once query variables for this particular
  query execution is known."
  [argument-value]
  (let [{:keys [type value]} argument-value]
    (case type

      :string [:scalar value]

      :integer [:scalar (Long/valueOf ^String value)]

      :float [:scalar (Double/valueOf ^String value)]

      :boolean [:scalar (Boolean/valueOf ^String value)]

      :null [:null nil]

      (:enum :variable) [type value]

      :object [:object (build-map-from-parsed-arguments value)]

      :array [:array (mapv xform-argument-value value)])))

(defn ^:private build-map-from-parsed-arguments
  "Builds a map from the parsed arguments."
  [parsed-arguments]
  (->> parsed-arguments
       (reduce (fn [m {:keys [arg-name arg-value]}]
                 ;; TODO: Check for duplicate arg names
                 (assoc! m arg-name (xform-argument-value arg-value)))
               (transient {}))
       persistent!))

(declare ^:private extract-reportable-arguments)

(defn ^:private extract-reportable-argument-value
  [[arg-type v]]
  (case arg-type
    :variable (symbol (str \$ (name v)))
    :array (mapv extract-reportable-argument-value v)
    :object (extract-reportable-arguments v)
    v))

(defn ^:private extract-reportable-arguments
  [arg-map]
  (map-vals extract-reportable-argument-value arg-map))

(defn ^:private scalar?
  [type]
  (-> type :category #{:scalar :enum} boolean))

(defmulti ^:private is-dynamic?
  "Given an argument tuple, returns true if the argument is dynamic
  (references a variable)."
  (fn [[type _]] type))

(defmethod is-dynamic? :default
  [_]
  false)

(defmethod is-dynamic? :variable
  [_]
  true)

;; For the composite types, have to see if any composed values are dynamic.
(defmethod is-dynamic? :array
  [[_ array-values]]
  (some is-dynamic? array-values))

(defmethod is-dynamic? :object
  [[_ object-map]]
  (some is-dynamic? (vals object-map)))

(defn ^:private split-arguments
  "Given a map of arguments, determines which are literal and which are dynamic.

  Returns a tuple of two maps. First map is simple arguments:
  arguments whose value is entirely static.  The second is dynamic arguments,
  whose value comes from a query variable."
  [arguments]
  (when arguments
    (loop [state nil
           args arguments]
      (if (seq args)
        (let [[k v] (first args)
              classification (if (is-dynamic? v)
                               :dynamic
                               :literal)]
          (recur (assoc-in state [classification k] v)
                 (next args)))
        [(:literal state) (:dynamic state)]))))

(defn ^:private collect-default-values
  [field-map]                                               ; also works with arguments
  (let [defaults (->> field-map
                      (map-vals :default-value)
                      (filter-vals some?))]
    (when-not (empty? defaults)
      defaults)))

(defn ^:private use-nested-type
  "Replaces the :type of the def with the nested type; this is used to strip off a
  :list or :non-null type before working on the underlying :root type."
  [any-def]
  (update any-def :type :type))

(defn ^:private coerce-to-multiple-if-list-type
  "Coerces single value to a list of size one if the value is not null
  and type is a list. Otherwise returns unmodified argument tuple."
  [argument-definition [arg-type arg-value :as arg-tuple]]
  (if (and (contains-modifier? :list argument-definition)
           (not (nil? arg-value))
           (not (sequential? arg-value)))
    [:array [[arg-type arg-value]]]
    arg-tuple))

(defn ^:private arg-tuple->value
  [[arg-type arg-value]]
  (case arg-type
    :scalar
    arg-value

    :array
    (map arg-tuple->value arg-value)

    :object
    (map-vals arg-tuple->value arg-value)))

(defmulti ^:private process-literal-argument
  "Validates a literal argument value to ensure it is compatible
  with the declared type of the field argument. Returns the underlying
  coorced value.

  arg-value is a tuple of argument type (:scalar, :enum, :null, :array, or :object) and
  the parsed value."

  (fn [_schema argument-definition [arg-type _]]
    (if (contains-modifier? :list argument-definition)
      ;; list types allow a single value on input
      :array
      arg-type)))

(defmethod process-literal-argument :scalar
  [schema argument-definition [_ arg-value]]
  (let [type-name (schema/root-type-name argument-definition)
        scalar-type (get schema type-name)]
    (with-exception-context {:value arg-value
                             :type-name type-name}
      ;; TODO: Special case for the all-too-popular "passed a string for an enum"
      (when-not (= :scalar (:category scalar-type))
        (throw-exception (format "A scalar value was provided for type %s, which is not a scalar type."
                                 (q type-name))
                         {:category (:category scalar-type)}))

      (cond-let
        (nil? arg-value)
        nil

        :let [parser (:parse scalar-type)
              coerced (try
                        (parser arg-value)
                        (catch Throwable t
                          (schema/coercion-failure (to-message t) (ex-data t))))]

        ;; The parser callback can return nil if it fails to perform the conversion
        ;; and get a generic message, or return a coercion-failure with more details.

        (nil? coerced)
        (throw-exception (format "Unable to convert %s to scalar type %s."
                                 (pr-str arg-value)
                                 (q type-name))
                         {:value arg-value
                          :type-name type-name})

        (schema/is-coercion-failure? coerced)
        (throw-exception (format "Scalar value is not parsable as type %s: %s"
                                 (q type-name)
                                 (:message coerced))
                         (dissoc coerced :message))

        :else
        coerced))))

(defmethod process-literal-argument :null
  [schema argument-definition arg-value]
  (when (non-null-kind? argument-definition)
    (throw-exception "An explicit null value was provided for a non-nullable argument."))

  nil)

(defmethod process-literal-argument :enum
  [schema argument-definition [_ arg-value]]
  ;; First, make sure the category is an enum
  (let [enum-type-name (schema/root-type-name argument-definition)
        type-def (get schema enum-type-name)]
    (with-exception-context {:value arg-value}
      (when-not (= :enum (:category type-def))
        (throw-exception "Enum value supplied for argument whose type is not an enum."
                         {:argument-type enum-type-name}))

      (or (get (:values-set type-def) arg-value)
          (throw-exception (format "Provided argument value %s is not member of enum type."
                                   (q arg-value))
                           {:allowed-values (:values-set type-def)
                            :enum-type enum-type-name})))))

(defmethod process-literal-argument :object
  [schema argument-definition arg-tuple]
  (let [[_ arg-value] arg-tuple
        type-name (schema/root-type-name argument-definition)
        schema-type (get schema type-name)
        schema-category (:category schema-type)]
    (cond

      ;; An input object has fields, some of which are required, some of which
      ;; have default values.
      (= :input-object schema-category)
      (let [object-fields (:fields schema-type)
            default-values (collect-default-values object-fields)
            required-keys (keys (filter-vals non-null-kind? object-fields))
            process-object-field (fn [m k v]
                                   (if-let [field (get object-fields k)]
                                     (assoc m k
                                            (process-literal-argument schema field v))
                                     (throw-exception (format "Input object contained unexpected key %s."
                                                              (q k))
                                                      {:schema-type type-name})))
            object-value (reduce-kv process-object-field
                                    {}
                                    arg-value)
            with-defaults (merge default-values object-value)]
        (doseq [k required-keys]
          (when (nil? (get with-defaults k))
            (throw-exception (format "No value provided for non-nullable key %s of input object %s."
                                     (q k)
                                     (q type-name))
                             {:missing-key k
                              :required-keys (sort required-keys)
                              :schema-type type-name})))
        with-defaults)

      (= :scalar schema-category)
      ;; A scalar usually accepts a string, but it can accept other types including even a map
      (process-literal-argument schema argument-definition
                                [:scalar (arg-tuple->value arg-tuple)])

      :else
      (throw-exception "Input object supplied for argument whose type is not an input object."
                       {:argument-type (:type-name schema-type)}))))

(defmethod process-literal-argument :array
  [schema argument-definition arg-tuple]
  (let [kind (-> argument-definition :type :kind)
       [_ arg-value :as arg-tuple*] (coerce-to-multiple-if-list-type argument-definition arg-tuple)]
    (case kind
      :non-null
      (recur schema (use-nested-type argument-definition) arg-tuple*)

      :root
      (throw-exception "Provided argument value is an array, but the argument is not a list.")

      :list
      (let [fake-argument-def (use-nested-type argument-definition)]
        (mapv #(process-literal-argument schema fake-argument-def %) arg-value)))))

(defn ^:private decapitalize
  [s]
  (str (-> s
           (subs 0 1)
           str/lower-case)
       (subs s 1)))

(defn ^:private construct-literal-arguments
  "Converts and validates all literal arguments from their parsed format into
  values ready to be used at execution time. Returns a nil, or a map of arguments and
  literal values."
  [schema argument-defs arguments]
  (let [default-values (collect-default-values argument-defs)]
    (if (empty? arguments)
      default-values
      (let [process-arg (fn [arg-name arg-value]
                          (with-exception-context {:argument arg-name}
                            (let [arg-def (get argument-defs arg-name)]

                              (when-not arg-def
                                (throw-exception (format "Unknown argument %s."
                                                         (q arg-name))
                                                 {:defined-arguments (keys argument-defs)}))
                              (try
                                (process-literal-argument schema arg-def arg-value)
                                (catch Exception e
                                  (throw-exception (format "For argument %s, %s"
                                                           (q arg-name)
                                                           (decapitalize (to-message e)))
                                                   nil
                                                   e))))))]
        (let [static-args (reduce-kv (fn [m k v]
                                       (assoc m k (process-arg k v)))
                                     nil
                                     arguments)
              with-defaults (merge default-values
                                   static-args)]
          (when-not (empty? with-defaults)
            with-defaults))))))

(defn ^:private compatible-types?
  [var-type arg-type]
  (let [v-kind (:kind var-type)
        a-kind (:kind arg-type)
        v-type (:type var-type)
        a-type (:type arg-type)]
    (cond

      ;; If the variable may not be null, but the argument is less precise,
      ;; then it's ok to continue; use the next type of the variable.
      (and (= v-kind :non-null)
           (not= a-kind :non-null))
      (recur v-type arg-type)

      ;; The opposite: the argument is non-null but the variable might be null, BUT
      ;; there's a default, then strip off a layer of argument type and continue.
      (and (= a-kind :non-null)
           (not= v-kind :non-null))
      (recur var-type a-type)

      ;; This is the special case where a single value variable may be promoted
      ;; for assignment to a list argument.

      (and (= a-kind :list)
           (not= v-kind :list))
      ;; Check if the type of the list argument is compatible, by stripping the :list qualifier
      ;; from the argument type.
      (recur var-type a-type)

      ;; At this point we've stripped off non-null on the arg or var side.  We should
      ;; be at a meeting point, either both :list or both :root.
      (not= a-kind v-kind)
      false

      ;; Then :list, strip that off to see if the element type of the list is compatible.
      ;; The default, if any, applied to the list, not the values inside the list.
      (not= :root a-kind)
      (recur v-type a-type)

      ;; Because arguments and variables are always scalars, enums, or input-objects, the
      ;; more complicated checks for unions and interfaces are not necessary.

      :else
      (= v-type a-type))))

(defn ^:private type-compatible?
  "Compares a variable definition against an argument definition to ensure they are
  compatible types. This is similar to schema/is-compatible-type? but has some special rules
  related to arguments."
  [var-def arg-def]
  (compatible-types? (:type var-def)
                     (:type arg-def)))

(defn ^:private build-type-summary
  "Converts nested type maps into the format used in a GraphQL query."
  [type-map]
  (let [nested (:type type-map)]
    (case (:kind type-map)
      :list
      (str "["
           (build-type-summary nested)
           "]")

      :non-null
      (str (build-type-summary nested) "!")

      :root
      (name nested))))

(defn ^:private summarize-type
  [type-def]
  (build-type-summary (:type type-def)))

(defmulti ^:private process-dynamic-argument
  "Processes a dynamic argument (one whose value is at least partly defined
   by a query variable) into a function that accepts the map of variable values,
   and returns the extracted variable value."
  (fn [schema argument-definition [arg-type _]]
    arg-type))

(defn ^:private construct-literal-argument
  [schema result argument-type arg-value]
  (cond-let
    :let [nested-type (:type argument-type)
          kind (:kind argument-type)]

    ;; we can only hit this if we iterate over list members
    (and (nil? result) (= :non-null kind))
    (throw-exception (format "Variable %s contains null members but supplies the value for a list that can't have any null members."
                             (q arg-value))
                     {:variable-name arg-value})

    (= :list kind)
    (cond
      (and (= :list (:kind nested-type))
           (not (sequential? (first result))))
      (throw-exception (format "Variable %s doesn't contain the correct number of (nested) lists."
                               (q arg-value))
                       {:variable-name arg-value})

      ;; variables of a list type allow for a single value input
      (and (some? result)
           (not (sequential? result)))
      [:array (mapv #(construct-literal-argument schema % nested-type arg-value) [result])]

      :else
      [:array (mapv #(construct-literal-argument schema % nested-type arg-value) result)])

    (nil? result)
    [:null nil]

    (map? nested-type)
    (recur schema result nested-type arg-value)

    :let [category (get-in schema [nested-type :category])]

    (= category :scalar)
    [:scalar result]

    ;; enums have to be handled carefully because they are likely strings in
    ;; the variable map.

    (= category :enum)
    [:enum (as-keyword result)]

    (= category :input-object)
    [:object (let [object-fields (get-in schema [nested-type :fields])]
               (reduce (fn [acc k]
                         (let [v (get result k)
                               field-type (get object-fields k)]
                           (when-not (contains? object-fields k)
                             (throw-exception "Field not defined for input object."
                                              {:field-name k
                                               :input-object-type nested-type
                                               :input-object-fields (-> object-fields keys sort vec)}))
                           (assoc acc k (construct-literal-argument schema v field-type arg-value))))
                       {}
                       (keys result)))]

    :else
    (throw (IllegalStateException. "Sanity check - no option in construct-literal-argument."))))

(defn ^:private substitute-variable
  "Checks result against variable kind, iterates over nested types, and applies respective
  actions, if necessary, e.g. parse for custom scalars."
  [schema result argument-type arg-value]
  (process-literal-argument schema {:type argument-type} (construct-literal-argument schema result argument-type arg-value)))

(defmethod process-dynamic-argument :variable
  [schema argument-definition arg]
  ;; ::variables is stashed into schema by xform-query
  (let [[_ arg-value] arg
        captured-context *exception-context*
        variable-def (get-in schema [::variables arg-value])]
    (when (nil? variable-def)
      (throw-exception (format "Argument references undeclared variable %s."
                               (q arg-value))
                       {:unknown-variable arg-value
                        :declared-variables (-> schema ::variables keys sort)}))

    (when-not (type-compatible? variable-def argument-definition)
      (throw-exception "Variable and argument are not compatible types."
                       {:argument-type (summarize-type argument-definition)
                        :variable-type (summarize-type variable-def)}))

    (let [var-non-nullable? (non-null-kind? variable-def)
          arg-non-nullable? (non-null-kind? argument-definition)
          var-has-default? (contains? variable-def :default-value)
          var-default (:default-value variable-def)]

      (fn [variables]
        (with-exception-context captured-context
          (cond-let
            :let [result (get variables arg-value)]

            ;; So, when a client provides variables, sometimes you get a string
            ;; when you expect a keyword for an enum. Can't help that, when the value
            ;; comes from a variable, there's no mechanism until we reach right here to convert it
            ;; to a keyword.

            (some? result)
            (substitute-variable schema result (:type argument-definition) arg-value)

            :let [supplied? (contains? variables arg-value)]

            (and (not supplied?)
                 var-has-default?)
            ;; There might just be an issue when the default is explicitly nil
            var-default

            ;; Either the variable was not specified OR an explicit null was specified
            var-non-nullable?
            (throw-exception (format "No value was provided for variable %s, which is non-nullable."
                                     (q arg-value))
                             {:variable-name arg-value})

            ;; An explicit nil was supplied for the variable, which may be a problem if the
            ;; argument doesn't accept nulls.
            supplied?
            (when arg-non-nullable?
              (throw-exception (format "Argument %s is required, but no value was provided."
                                       (q arg-value))
                               {:argument (:qualified-name argument-definition)}))

            (contains? argument-definition :default-value)
            (:default-value argument-definition)

            ;; No value or default is supplied (or needed); the resolver will simply not
            ;; see the argument in its argument map. It can decide what to do.

            :else
            ::omit-argument))))))

(declare ^:private process-arguments)

(defmethod process-dynamic-argument :object
  [schema argument-definition arg]
  (let [object-fields (->> argument-definition :type :type (get schema) :fields)
        [literal-values dynamic-extractor] (process-arguments schema object-fields (second arg))]
    (fn [arguments]
      (merge literal-values
             (dynamic-extractor arguments)))))

(defn ^:private construct-dynamic-arguments-extractor
  [schema argument-definitions arguments]
  (when-not (empty? arguments)
    (let [process-arg (fn [arg-name arg-value]
                        (let [arg-def (get argument-definitions arg-name)
                              arg-type-name (schema/root-type-name arg-def)
                              arg-type (get schema arg-type-name)]
                          (with-exception-context {:argument arg-name}
                            (when (and (= :scalar (:category arg-type))
                                       (= :object (first arg-value)))
                              (throw-exception (format "Argument %s contains a scalar argument with nested variables, which is not allowed."
                                                       (q arg-name))
                                               nil))

                            (when-not arg-def
                              (throw-exception (format "Unknown argument %s."
                                                       (q arg-name))
                                               {:field-arguments (keys argument-definitions)}))
                            (try
                              (process-dynamic-argument schema arg-def arg-value)
                              (catch Exception e
                                (throw-exception (format "For argument %s, %s"
                                                         (q arg-name)
                                                         (decapitalize (to-message e)))
                                                 nil
                                                 e))))))
          dynamic-args (reduce-kv (fn [m k v]
                                    (assoc m k (process-arg k v)))
                                  nil
                                  arguments)]
      ;; This is kind of a juxt buried in a map. Each value is a function that accepts
      ;; the variables and returns the actual value to use.
      (fn [variables]
        (->> (map-vals #(% variables) dynamic-args)
             ;; Some arguments may have a null value, or a null default value.
             ;; However, if the argument is not specified at all, and has no default value
             ;; then the ::omit-argument value is provided, and that marks an argument to
             ;; be removed entirely.
             (remove-vals #(= % ::omit-argument)))))))

(defn ^:private disj*
  [set ks]
  (apply disj set ks))

(defn ^:private required-argument?
  [arg-def]
  (and (non-null-kind? arg-def)
       (not (contains? arg-def :default-value))))

(defn ^:private process-arguments
  "Processes arguments to a field or a directive, doing some organizing and some
   validation.

  Returns a tuple of the literal argument values and a function to extract the dynamic argument
  values from the map of query variables."
  [schema argument-definitions arguments]
  (let [[literal-args dynamic-args] (split-arguments arguments)
        literal-argument-values (construct-literal-arguments schema argument-definitions literal-args)
        dynamic-extractor (construct-dynamic-arguments-extractor schema argument-definitions dynamic-args)
        missing-keys (-> argument-definitions
                         (as-> $ (filter-vals required-argument? $))
                         keys
                         set
                         (disj* (keys literal-args))
                         (disj* (keys dynamic-args))
                         sort)]
    ;; So, for literal arguments, we've already done a null check on non-nullable arguments.
    ;; For dynamic (variable based) arguments, there's a parse-time check that the
    ;; argument is mated to an appropriate variable, and a execution-time check that
    ;; the variable is non-null.
    ;; However, there might be omitted variables that are non-nullable.
    (when (seq missing-keys)
      (throw-exception "Not all non-nullable arguments have supplied values."
                       {:missing-arguments missing-keys}))

    [literal-argument-values dynamic-extractor]))

(defn ^:private default-node-map
  "Returns a map with the query path to the node and the location in the
  document."
  [selection]
  {:location (meta selection)})

(defn ^:private node-context
  [node-map]
  {:locations [(:location node-map)]})

(defn ^:private convert-parsed-directives
  "Passed a seq of parsed directive nodes, returns a seq of executable directives."
  [schema parsed-directives]
  (let [f (fn [parsed-directive]
          (let [{directive-name :directive-name} parsed-directive]
            (with-exception-context {:directive directive-name}
              (if-let [directive-def (get builtin-directives directive-name)]
                (let [[literal-arguments dynamic-arguments-extractor]
                      (try
                        (process-arguments schema
                                           (:args directive-def)
                                           (-> parsed-directive :args build-map-from-parsed-arguments))
                        (catch ExceptionInfo e
                          (throw-exception (format "Exception applying arguments to directive %s: %s"
                                                   (q directive-name)
                                                   (to-message e))
                                           nil
                                           e)))]
                  (assoc parsed-directive
                         :effector (:effector directive-def)
                         :arguments literal-arguments
                         ::arguments-extractor dynamic-arguments-extractor))
                (throw-exception (format "Unknown directive %s."
                                         (q directive-name)
                                         {:unknown-directive directive-name
                                          :available-directives (-> builtin-directives keys sort)}))))))]
    (mapv f parsed-directives)))

(def ^:private typename-field-definition
  "A psuedo field definition that exists to act as a placeholder when the
  __typename metafield is encountered."
  {:type {:kind :non-null
          :type {:kind :root
                 :type :String}}

   :field-name :__typename

   :resolve (fn [context _ _]
              (-> context
                  :com.walmartlabs.lacinia/container-type-name
                  resolve/resolve-as))

   :selector schema/floor-selector})

(defn ^:private prepare-parsed-field
  [parsed-field]
  (let [{:keys [alias field-name selections directives args]} parsed-field
        arguments (build-map-from-parsed-arguments args)]
    (-> {:field field-name
         :alias alias
         :selections selections
         :directives directives}
        (assoc-seq? :arguments arguments)
        (assoc-seq? :reportable-arguments (extract-reportable-arguments arguments)))))

(defn ^:private select-operation
  "Given a collection of parsed operation definitions and an operation name (which
  might be nil), retrieve the requested operation definition from the document."
  [operations operation-name]
  (cond-let
    :let [operation-key (when-not (str/blank? operation-name)
                          (as-keyword operation-name))
          operation-count (count operations)
          single-op? (= 1 operation-count)
          first-op (first operations)]

    (and single-op?
         operation-key
         (not= operation-key (:name first-op)))

    (throw-exception "Single operation did not provide a matching name."
                     {:op-name operation-name})

    single-op?
    first-op

    :let [operation (first-match #(= operation-key (:name %)) operations)]

    (nil? operation)
    (throw-exception "Multiple operations provided but no matching name found."
                     {:op-count operation-count
                      :operation-name operation-name})

    ;; TODO: Check the spec, seems like if there are multiple operations, they
    ;; should all be named with unique names.

    :else operation))

(def ^:private prepare-keys
  "Seq of keys associated with prepare phase operations."
  [::prepare-directives? ::prepare-dynamic-arguments? ::prepare-nested-selections? ::needs-prepare?])

(defn ^:private mark-node-for-prepare
  "Marks up a node so that it will, during the prepare phase, have the
  proper operations performed on it.  A node may have directives,
  a node may be a field with arguments, a node may be a field or inline fragment
  with nested selections."
  [node]
  (let [directives? (-> node :directives some?)
        dynamic-arguments? (-> node ::arguments-extractor some?)
        selections-need-prepare? (->> node
                                      :selections
                                      (some ::needs-prepare?)
                                      some?)]
    (cond-> node
      directives? (assoc ::prepare-directives? true)
      dynamic-arguments? (assoc ::prepare-dynamic-arguments? true)
      selections-need-prepare? (assoc ::prepare-nested-selections? true)
      (or directives? dynamic-arguments? selections-need-prepare?)
      (assoc ::needs-prepare? true))))

(defn ^:private compute-arguments
  [node variables]
  (let [{:keys [arguments ::arguments-extractor]} node]
    (cond-> arguments
      arguments-extractor (merge (arguments-extractor variables)))))

(defn ^:private apply-directives
  "Computes final arguments for each directive, and passes the node through each
  directive's effector."
  [node variables]
  (reduce (fn [node directive]
            (let [effector (:effector directive)]
                 (effector node (compute-arguments directive variables))))
          node
          (:directives node)))

(defn ^:private apply-dynamic-arguments
  "Computes final arguments for a field from its literal arguments and dynamic arguments."
  [node variables]
  (assoc node :arguments (compute-arguments node variables)))

(declare ^:private prepare-node)

(defn ^:private prepare-nested-selections
  [node variables]
  (let [f #(prepare-node % variables)]
    (update node :selections
            #(keepv f %))))

(defn ^:private prepare-node
  [node variables]
  ;; Most nodes don't need anything and we're done
  (if-not (::needs-prepare? node)
    node
    (let [{:keys [::prepare-directives?
                  ::prepare-dynamic-arguments?
                  ::prepare-nested-selections?]} node
          node' (cond-> node
                  prepare-directives? (apply-directives variables))]
      ;; Directives work by modifying the node. Deleting the node entirely
      ;; would be nice, but that leaves errors about "must have a sub selection"
      ;; so we set the disabled flag instead.
      (if (:disabled? node')
        node'
        ;; No need to do work further down the tree if the node itself is
        ;; disabled
        (cond-> node'
          prepare-dynamic-arguments? (apply-dynamic-arguments variables)
          prepare-nested-selections? (prepare-nested-selections variables))))))

(defn ^:private to-selection-key
  "The selection key only applies to fields (not fragments) and
  consists of the field name or alias, and the arguments."
  [selection]
  (case (:selection-type selection)
    :field
    (:alias selection)

    ;; TODO: This may be too simplified ... worried about loss of data when merging things together
    ;; at runtime.
    (gensym "fragment-")))

(declare ^:private coalesce-selections)

(defn ^:private merge-selections
  [first-selection second-selection]
  (when-not (= (:reportable-arguments first-selection)
               (:reportable-arguments second-selection))
    (let [{:keys [qualified-name]} (:field-definition first-selection)]
      (throw (ex-info (format "Different selections of %s have incompatible arguments. Use alias names if this is intentional."
                              (q qualified-name))
                      {:field-name qualified-name
                       :arguments (:reportable-arguments first-selection)
                       :incompatible-arguments (:reportable-arguments second-selection)}))))
  (let [combined-selections (coalesce-selections (concat (:selections first-selection)
                                                         (:selections second-selection)))
        prepare-values (select-keys second-selection prepare-keys)]
    (-> first-selection
        (assoc :selections combined-selections)
        (cond->
          (seq prepare-values) (-> (merge prepare-values)
                                   (assoc ::needs-prepare? true))))))

(defn ^:private coalesce-selections
  "It is possible to select the same field more than once, and then identify different
  selections within that field. The results should merge together, and match the query
  order as closely as possible. This is tricky, and recursive."
  [selections]
  (if (= 1 (count selections))
    selections
    (let [reducer (fn [m selection]
                    (let [selection-key (to-selection-key selection)]
                      (if-let [prev-selection (get m selection-key)]
                        (assoc m selection-key (merge-selections prev-selection selection))
                        (assoc m selection-key selection))))]
      (->> selections
           (reduce reducer (ordered-map))
           vals))))
;
(defn ^:private normalize-selections
  "Starting with a selection (a field or fragment) recursively normalize any nested selections selections,
  and handle marking the node for any necessary prepare phase operations."
  [schema m type]
  (let [sub-selections (:selections m)]
    (mark-node-for-prepare
      (if (seq sub-selections)
        (assoc m :selections (->> sub-selections
                                  (mapv #(selection schema % type))
                                  coalesce-selections))
        m))))

(defn ^:private expand-fragment-type-to-concrete-types
  "Expands a single type to a set of concrete types names.  For unions, this is
  just the union members (each a concrete type name).

  For interfaces, this is the names of concrete classes that
  implement the interface.

  For a concrete type, this is simply the type's name as a single value set."
  [condition-type]
  (case (:category condition-type)

    (:interface :union) (:members condition-type)

    :object (hash-set (:type-name condition-type))

    (throw-exception (format "Fragment cannot condition on non-composite type %s."
                             (-> condition-type :type-name q)))))

(defn ^:private finalize-fragment-def
  [schema def]
  (let [fragment-type (get schema (:type def))
        concrete-types (expand-fragment-type-to-concrete-types fragment-type)]
    (-> def
        (dissoc :fragment-name)
        (assoc :concrete-types concrete-types))))

(defn ^:private normalize-fragment-definitions
  "Given a collection of fragment definitions, transform them into a map of the
  form {:<definition-name> {...}}."
  [schema fragment-definitions]
  (let [f (fn [def]
            (let [defaults {:location (meta def)}
                  {:keys [on-type fragment-name selections directives]} def
                  m (-> defaults
                        (assoc :fragment-name fragment-name
                               :type on-type
                               :selections selections)
                        (cond-> directives
                          (assoc :directives (convert-parsed-directives schema directives))))
                  fragment-type (get schema on-type)]
              ;; TODO: Verify fragment type exists
              (normalize-selections schema
                                    m
                                    fragment-type)))]
    (into {} (comp (map f)
                   (map (juxt :fragment-name
                              #(finalize-fragment-def schema %))))
          fragment-definitions)))

(defmulti ^:private selection
  "A recursive function that parses the parsed query tree structure into the
   format used during execution; this involves tracking the current schema type
   (initially, nil)."
  (fn [_schema parsed-selection _type]
    (:type parsed-selection)))

(defmethod selection :field
  [schema parsed-field type]
  (let [defaults (default-node-map parsed-field)
        context (node-context defaults)
        result (with-exception-context context
                 (merge defaults (prepare-parsed-field parsed-field)))
        {:keys [field alias arguments reportable-arguments directives]} result
        is-typename-metafield? (= field :__typename)
        field-definition (if is-typename-metafield?
                           typename-field-definition
                           (get-in type [:fields field]))
        field-type (schema/root-type-name field-definition)
        nested-type (get schema field-type)
        selection (with-exception-context (assoc context :field field)
                    (when (nil? nested-type)
                      (if (scalar? type)
                        (throw-exception "Path de-references through a scalar type.")
                        (let [type-name (:type-name type)]
                          (throw-exception (format "Cannot query field %s on type %s."
                                                   (q field)
                                                   (if type-name
                                                     (q type-name)
                                                     "UNKNOWN"))
                                           {:type type-name}))))
                    (let [[literal-arguments dynamic-arguments-extractor]
                          (try
                            (process-arguments schema
                                               (:args field-definition)
                                               arguments)
                            (catch ExceptionInfo e
                              (throw-exception (format "Exception applying arguments to field %s: %s"
                                                       (q field)
                                                       (to-message e))
                                               nil
                                               e)))]
                      (assoc result
                             :selection-type :field
                             :directives (convert-parsed-directives schema directives)
                             :alias (or alias field)
                             :leaf? (scalar? nested-type)
                             :concrete-type? (or is-typename-metafield?
                                                 (-> type :category #{:object :input-object} some?))
                             :reportable-arguments reportable-arguments
                             :arguments literal-arguments
                             ::arguments-extractor dynamic-arguments-extractor
                             :field-definition field-definition)))]
    (normalize-selections schema selection nested-type)))

(defmethod selection :inline-fragment
  [schema parsed-inline-fragment _type]
  (let [defaults (default-node-map parsed-inline-fragment)]
    (with-exception-context (node-context defaults)
      (let [{type-name :on-type
             :keys [selections directives]} parsed-inline-fragment
            selection (merge defaults
                             {:selections selections})
            fragment-type (get schema type-name)]

        (when (nil? fragment-type)
          (throw-exception (format "Inline fragment has a type condition on unknown type %s."
                                   (q type-name))))

        (let [concrete-types (expand-fragment-type-to-concrete-types fragment-type)
              inline-fragment (-> selection
                                  (assoc :selection-type :inline-fragment
                                         :concrete-types concrete-types)
                                  (cond-> directives (assoc :directives (convert-parsed-directives schema directives))))]
          (normalize-selections schema
                                inline-fragment
                                fragment-type))))))

(defmethod selection :named-fragment
  [schema parsed-fragment _type]
  (let [defaults (default-node-map parsed-fragment)
        {:keys [fragment-name directives]} parsed-fragment]
    (with-exception-context (node-context defaults)
      ;; TODO: Verify that fragment name exists?
      (-> defaults
          (merge {:selection-type :fragment-spread
                  :fragment-name fragment-name})
          (cond-> directives (assoc :directives (convert-parsed-directives schema directives)))
          mark-node-for-prepare))))

(defn ^:private construct-var-type-map
  "Converts a var-type (in the parsed format) into a similar stucture that
  matches how the schema identifies types."
  [parsed]
  (case (:type parsed)

    :root-type
    {:kind :root
     :type (:type-name parsed)}

    :list
    {:kind :list
     :type (-> parsed :of-type construct-var-type-map)}

    :non-null
    {:kind :non-null
     :type (-> parsed :of-type construct-var-type-map)}

    (throw-exception "Unable to parse variable type.")))

(defn ^:private compose-variable-definition
  "Converts a parsed variable definition into a tuple of variable name, and
  schema-type (as with an argument definition)."
  [schema parsed-var-def]
  (let [{:keys [var-name var-type]
         default-value :default} parsed-var-def
        var-def {:type (construct-var-type-map var-type)}
        ;; Simulate a field definition around the raw type:
        type-name (schema/root-type-name var-def)
        schema-type (get schema type-name)]
    (with-exception-context {:var-name var-name
                             :type-name type-name
                             :schema-types (schema/type-map schema)}
      (when (nil? schema-type)
        (throw-exception (format "Variable %s definition references unknown type %s."
                                 (q var-name)
                                 (q type-name))))

      (when-not (#{:scalar :enum :input-object} (:category schema-type))
        (throw-exception (format "Variable %s is not defined as a scalar, enumerated type, or input object."
                                 (q var-name)))))

    [var-name (cond-> var-def
                default-value (assoc :default-value (->> default-value
                                                         xform-argument-value
                                                         ;; The variable definition is the right format to act as a
                                                         ;; "fake" argument.
                                                         (process-literal-argument schema var-def))))]))

(defn ^:private extract-variable-definitions
  [schema operation]
  (when-let [var-definitions (:vars operation)]
    ;; TODO: Check for conflicting variable names
    (into {}
          (map #(compose-variable-definition schema %)
               var-definitions))))

(defn ^:private operation-type->root
  [schema operation-type]
  (let [type-name (get-in schema [::schema/roots operation-type])]
    (get schema type-name)))

(defn ^:private categorize-root
  "Categorizes a root parsed node, which is either a fragment definition or one of the operation types."
  [root]
  (if (-> root :type (= :fragment-definition))
    :fragment-definition
    :operation-definition))

(defn ^:private xform-query
  "Given an the intermediate parsed query, traverses and reforms into a
  form expected by the executor."
  [schema parsed-roots operation-name]
  (let [{:keys [fragment-definition operation-definition]}
        (group-by categorize-root parsed-roots)

        operation
        (select-operation operation-definition operation-name)

        operation-type (:type operation)

        root (operation-type->root schema operation-type)

        variable-definitions (extract-variable-definitions schema operation)

        selections (:selections operation)

        ;; Clumsy but necessary way to let lower levels know about variable definitions.
        ;; This will deviate from the spec slightly: all fragments will be transformed
        ;; and validated using the variables from the selected operation, even those that
        ;; are not referenced by the selected operation (another operation may define
        ;; different variables). A solution might be to collect up the fragments that
        ;; are referenced inside the operation, validate those, discard the rest.
        schema' (assoc schema ::variables variable-definitions)

        ;; Explicitly defeat some lazy evaluation, to ensure that validation exceptions are thrown
        ;; from within this function call.
        selections (coalesce-selections (mapv #(selection schema' % root) selections))]

    (when (and (= :subscription operation-type)
               (not= 1 (count selections)))
      (throw (IllegalStateException. "Subscriptions only allow exactly one selection for the operation.")))

    ;; Build the result describing the fragments and selections (for the selected operation).
    {:fragments (normalize-fragment-definitions schema' fragment-definition)
     :selections selections
     :operation-type operation-type
     :root root
     constants/schema-key schema}))

(defn prepare-with-query-variables
  "Given a parsed query data structure and a map of variables,
  update the query, calculating field arguments and applying directives."
  [parsed-query variables]
  (let [prepare #(prepare-node % variables)]
    (-> (prepare-nested-selections parsed-query variables)
        (update :fragments #(map-vals prepare %)))))

(defn parse-query
  "Given a compiled schema and a query document, parses the query to an executable form
   as well as performing a number of validations.

   When the request containing the query document provides an operation name, that is provided
   and the parsed query executes just that operation."
  ([schema query-document]
   (parse-query schema query-document nil))
  ;; This version is rarely used: it assumes that document defines multiple named operations and only
  ;; one is being selected.
  ([schema query-document operation-name]
   (when-not (schema/compiled-schema? schema)
     (throw (IllegalStateException. "The provided schema has not been compiled.")))
   (xform-query schema (qp/parse-query query-document) operation-name)))

(defn operations
  "Given a previously parsed query, this returns a map of two keys:

  :type
  : The type of request, one of: :query, :mutation, or :subscription.

  :operations
  : The names of the top-level operations, as a set of keywords."
  {:added "0.17.0"}
  [parsed-query]
  (let [{:keys [operation-type selections]} parsed-query]
    {:type operation-type
     :operations (->> selections
                      (map #(get-in % [:field-definition :field-name]))
                      set)}))

(declare ^:private summarize-selection)

(defn ^:private summarize-selections
  [parsed-query selections]
  (str "{"
       (->> selections
            (mapcat #(summarize-selection parsed-query %))
            sort
            (str/join " "))
       "}"))

(defn ^:private summarize-selection
  [parsed-query selection]
  (case (:selection-type selection)

    :field
    (let [field-name (-> selection :field-definition :field-name name)]
      [(if (:leaf? selection)
         field-name
         (str field-name " " (summarize-selections parsed-query (:selections selection))))])

    :inline-fragment
    (mapcat #(summarize-selection parsed-query %) (:selections selection))

    :fragment-spread
    (let [{:keys [fragment-name]} selection
          fragment-selections (get-in parsed-query [:fragments fragment-name :selections])]
      (mapcat #(summarize-selection parsed-query %) fragment-selections))

    (throw (ex-info "Sanity check" {:selection selection}))))

(defn summarize-query
  "Analyzes a parsed query, returning a summary string.

  The summary superficially resembles a GraphQL query, but
  strips out aliases, directives, and field arguments. In addition, fragments (both inline and named)
  are collapsed into their containing selections.

  This summary can act as a 'fingerprint' of a related set of queries and is typically used
  in query performance analysis."
  {:added "0.26.0"}
  [parsed-query]
  (->> parsed-query
       :selections
       (summarize-selections parsed-query)))


