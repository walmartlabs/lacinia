(ns com.walmartlabs.lacinia.parser
  "Parsing of client querys using the ANTLR grammar."
  (:require [clj-antlr.core :as antlr.core]
            [clj-antlr.proto :as antlr.proto]
            [clojure.java.io :as io]
            [clj-antlr.common :as antlr.common]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.internal-utils
             :refer [cond-let update? q map-vals filter-vals
                     with-exception-context throw-exception to-message
                     keepv as-keyword]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.constants :as constants]
            [clojure.spec :as s])
  (:import (org.antlr.v4.runtime.tree ParseTree TerminalNode)
           (org.antlr.v4.runtime Parser ParserRuleContext Token)
           (clj_antlr ParseError)
           (clojure.lang ExceptionInfo)))

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

(def ^:private grammar
  (antlr.core/parser (slurp (io/resource "com/walmartlabs/lacinia/Graphql.g4"))))

(defn ^:private attach-location-as-meta
  "Attaches location information {:line ... :column ...} as metadata to the
  sexp."
  [^ParseTree t sexp]
  (when sexp
    (let [^Token token (.getStart ^ParserRuleContext t)]
      (with-meta
        sexp
        {:line (.getLine token)
         :column (.getCharPositionInLine token)}))))

(def ^:private ignored-terminals
  "Textual fragments which are to be immediately discarded as they have no
  relevance to a formed parse tree."
  #{"'{'" "'}'" "'('" "')'" "'['" "']'" "'...'" "'fragment'" "'on'"
    "':'" "'='" "'$'" "'!'" "\"" "'@'"})

(defn ^:private ignored-terminal?
  [token-name]
  (some? (some ignored-terminals #{token-name})))

(defn ^:private token-name
  "Returns the rule name of a terminal node, eg. :alias or :field."
  [^TerminalNode ctx ^Parser parser]
  (let [sym (.getSymbol ctx)
        idx (.getType sym)]
    (when-not (neg? idx)
      (aget (.getTokenNames parser) idx))))

(defn ^:private traverse
  "Recurses through a ParseTree, returning a tree structure of the form
  (:document [(:operationDefinition ...]).  Location information is attached
  as metadata."
  [^ParseTree t ^Parser p]
  (if (instance? ParserRuleContext t)
    (let [node (cons (->> (.getRuleIndex ^ParserRuleContext t)
                          (antlr.common/parser-rule-name p)
                          antlr.common/fast-keyword)
                     (keepv (comp
                              #(attach-location-as-meta t %)
                              #(traverse % p))
                            (antlr.common/children t)))]
      (if-let [e (.exception ^ParserRuleContext t)]
        (with-meta (list :clj-antlr/error node)
          {:error (antlr.common/recognition-exception->map e)})
        node))

    (let [token-name* (token-name t p)]
      (when-not (ignored-terminal? token-name*)
        (list (keyword (str/lower-case token-name*))
              (.getText t))))))

(declare ^:private xform-argument-map)

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
  ;;[:<type> <literal-value>]
  [argument-value]
  (let [[type first-value & _] argument-value]
    (case type
      ;; Because of how parsing works, the string literal includes the enclosing
      ;; quotes.
      :stringvalue [:scalar (subs first-value
                                  1
                                  (dec (.length ^String first-value)))]
      ;; For these other types, the value is still in string format, and will be
      ;; conformed a bit later.
      :intvalue [:scalar first-value]
      :floatvalue [:scalar first-value]
      :booleanvalue [:scalar first-value]
      :nullvalue [:null nil]
      :enumValue [:enum (-> first-value second as-keyword)]
      :objectValue [:object (xform-argument-map (next argument-value))]
      :arrayValue [:array (mapv (comp xform-argument-value second) (next argument-value))]
      :variable [:variable (-> first-value second keyword)])))

(defn ^:private xform-argument-map
  [nodes]
  (->> nodes
       (reduce (fn [m [_ [_ k] [_ v]]]
                 (assoc! m
                         (keyword k)
                         (xform-argument-value v)))
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

(defn ^:private node-reducer
  "A generic reducing fn for building maps out of nodes."
  [acc [k :as node]]
  (case k
    :name
    (assoc acc :field (keyword (second node)))

    :alias
    (assoc acc :alias (keyword (second (second node))))

    :arguments
    (let [args (xform-argument-map (rest node))]
      (assoc acc
             :arguments args
             :reportable-arguments (extract-reportable-arguments args)))

    :typeCondition
    ;; Part of inline fragments and fragment definitions
    (assoc acc :type (keyword (second (second (second node)))))

    :fragmentName
    ;; Part of a fragment reference (... ADefinedFragment)
    (assoc acc :fragment-name (keyword (second (second node))))

    :directives
    (->> (rest node)
         (reduce (fn ([acc [_ [_ k] v]]
                       ;; TODO: Spec indicates that directives must be unique by name
                      (assoc! acc (keyword k) (node-reducer {} v))))
                 (transient {}))
         persistent!
         (assoc acc :directives))

    :selectionSet
    ;; Keep the order of the selections (fields, inline fragments, and fragment spreads) so the
    ;; output will match the order in the request.
    (assoc acc :selections (rest node))))

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
  (loop [state nil
         args arguments]
    (if (seq args)
      (let [[k v] (first args)
            classification (if (is-dynamic? v)
                             :dynamic
                             :literal)]
        (recur (assoc-in state [classification k] v)
               (next args)))
      [(:literal state) (:dynamic state)])))

(defn ^:private assert-not-multiple
  [argument-definition]
  (when (contains-modifier? :list argument-definition)
    (throw-exception "A single argument value was provided for a list argument.")))

(defn ^:private collect-default-values
  [field-map]                                               ; also works with arguments
  (->> field-map
       (map-vals :default-value)
       (filter-vals identity)))

(defn ^:private use-nested-type
  "Replaces the :type of the def with the nested type; this is used to strip off a
  :list or :non-null type before working on the underlying :root type."
  [any-def]
  (update any-def :type :type))

(defmulti ^:private process-literal-argument
  "Validates a literal argument value to ensure it is compatible
  with the declared type of the field argument. Returns the underlying
  coorced value.

  arg-value is a tuple of argument type (:scalar, :enum, :null, :array, or :object) and
  the parsed value."

  (fn [schema argument-definition [arg-type _]]
    arg-type))

(defmethod process-literal-argument :scalar
  [schema argument-definition [_ arg-value]]
  (assert-not-multiple argument-definition)
  (let [type-name (schema/root-type-name argument-definition)
        scalar-type (get schema type-name)]
    (with-exception-context {:value arg-value
                             :type-name type-name}
      ;; TODO: Special case for the all-too-popular "passed a string for an enum"
      (when-not (= :scalar (:category scalar-type))
        (throw-exception (format "A scalar value was provided for type %s, which is not a scalar type."
                                 (q type-name))
                         {:category (:category scalar-type)}))

      (let [coerced (-> scalar-type :parse (s/conform arg-value))]
        (when (= ::s/invalid coerced)
          (throw-exception (format "Scalar value is not parsable as type %s."
                                   (q type-name))))
        coerced))))

(defmethod process-literal-argument :null
  [schema argument-definition arg-value]
  (when (-> argument-definition :type :kind (= :non-null))
    (throw-exception "An explicit null value was provided for a non-nullable argument."))

  nil)

(defmethod process-literal-argument :enum
  [schema argument-definition [_ arg-value]]
  (assert-not-multiple argument-definition)
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
  [schema argument-definition [_ arg-value]]
  (assert-not-multiple argument-definition)
  (let [type-name (schema/root-type-name argument-definition)
        schema-type (get schema type-name)]
    (when-not (= :input-object (:category schema-type))
      (throw-exception "Input object supplied for argument whose type is not an input object."
                       {:argument-type (:type-name schema-type)}))

    ;; An input object has fields, some of which are required, some of which
    ;; have default values.

    (let [object-fields (:fields schema-type)
          default-values (collect-default-values object-fields)
          required-keys (keys (filter-vals :non-nullable? object-fields))
          process-object-field (fn [m k v]
                                 (if-let [field (get object-fields k)]
                                   (assoc m k
                                          (process-literal-argument schema field v))
                                   (throw-exception (format "Input object contained unexpected key %s."
                                                            (q k))
                                                    {:schema-type type-name})))
          object-value (reduce-kv process-object-field
                                  nil
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
      with-defaults)))

(defmethod process-literal-argument :array
  [schema argument-definition [_ arg-value :as arg-tuple]]
  (let [kind (-> argument-definition :type :kind)]
    (case kind
      :non-null
      (recur schema (use-nested-type argument-definition) arg-tuple)

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
  "Converts and validates all literal arguments from their psuedo-Antlr format into
  values ready to be used at execution time. Returns a nil, or a map of arguments and
  literal values."
  [schema argument-defs arguments]
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
          with-defaults (merge (collect-default-values argument-defs)
                               static-args)]
      (when-not (empty? with-defaults)
        with-defaults))))

(defn ^:private compatible-types?
  [var-type arg-type var-has-default?]
  (let [v-kind (:kind var-type)
        a-kind (:kind arg-type)
        v-type (:type var-type)
        a-type (:type arg-type)]
    (cond

      ;; If the variable may not be null, but the argument is less precise,
      ;; then it's ok to continue; use the next type of the variable.
      (and (= v-kind :non-null)
           (not= a-kind :non-null))
      (recur v-type arg-type var-has-default?)

      ;; The opposite: the argument is non-null but the variable might be null, BUT
      ;; there's a default, then strip off a layer of argument type and continue.
      (and (= a-kind :non-null)
           (not= v-kind :non-null)
           var-has-default?)
      (recur var-type a-type var-has-default?)

      ;; At this point we've stripped off non-null on the arg or var side.  We should
      ;; be at a meeting point, either both :list or both :root.
      (not= a-kind v-kind)
      false

      ;; Then :list, strip that off to see if the element type of the list is compatible.
      ;; The default, if any, applied to the list, not the values inside the list.
      ;; TODO: This feels suspect, handling of list types is probably more complex than this.
      (not= :root a-kind)
      (recur v-type a-type false)

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
                     (:type arg-def)
                     (-> var-def :default-value some?)))

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

(defn ^:private non-null-kind?
  "Peeks at the kind of the provided def (field, argument, or variable) to see if it is :non-null"
  [any-def]
  (-> any-def :type :kind (= :non-null)))

(defn ^:private process-result
  "Checks result against variable kind, iterates over nested types, and applies respective
  actions, if necessary, e.g. parse for custom scalars."
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
    (if (not (sequential? result))
      (throw-exception (format "Variable %s doesn't contain the correct number of (nested) lists."
                               (q arg-value))
                       {:variable-name arg-value})
      (mapv #(process-result schema % nested-type arg-value) result))

    (nil? result)
    nil

    (map? nested-type)
    (recur schema result nested-type arg-value)

    :let [category (when (= :root kind)
                     (get-in schema [nested-type :category]))]

    (= category :scalar)
    (process-literal-argument schema {:type argument-type} [:scalar result])

    ;; enums have to be handled carefully because they are likely strings in
    ;; the variable map.

    (= category :enum)
    (process-literal-argument schema {:type argument-type} [:enum (as-keyword result)])

    :else
    (throw (IllegalStateException. "Sanity check - no option in process-result."))))

(defmethod process-dynamic-argument :variable
  [schema argument-definition [_ arg-value]]
  ;; ::variables is stashed into schema by xform-query
  (let [variable-def (get-in schema [::variables arg-value])]

    (when (nil? variable-def)
      (throw-exception (format "Argument references undeclared variable %s."
                               (q arg-value))
                       {:unknown-variable arg-value
                        :declared-variables (-> schema ::variables keys sort)}))

    (when-not (type-compatible? variable-def argument-definition)
      (throw-exception "Variable and argument are not compatible types."
                       {:argument-type (summarize-type argument-definition)
                        :variable-type (summarize-type variable-def)}))

    (let [{:keys [default-value]} argument-definition
          non-nullable? (non-null-kind? argument-definition)
          var-non-nullable? (non-null-kind? variable-def)
          var-default-value (:default-value variable-def)]
      (fn [variables]
        (cond-let
          :let [result (get variables arg-value)]

          ;; So, when a client provides variables, sometimes you get a string
          ;; when you expect a keyword for an enum. Can't help that, when the alue
          ;; comes from a variable, there's no mechanism until we reach right here to convert it
          ;; to a keyword.

          (some? result)
          (process-result schema result (:type argument-definition) arg-value)

          ;; TODO: This is only triggered if a variable is referenced, omitting a non-nillable
          ;; variable should be an error, regardless.
          var-non-nullable?
          (throw-exception (format "No value was provided for variable %s, which is non-nullable."
                                   (q arg-value))
                           {:variable-name arg-value})

          (some? var-default-value)
          var-default-value

          (some? default-value)
          default-value

          non-nullable?
          (throw-exception (format "Variable %s is null, but supplies the value for a non-nullable argument."
                                   (q arg-value))
                           {:variable-name arg-value})

          :else
          nil)))))

(defn ^:private construct-dynamic-arguments-extractor
  [schema argument-definitions arguments]
  (when-not (empty? arguments)
    (let [process-arg (fn [arg-name arg-value]
                        (let [arg-def (get argument-definitions arg-name)]
                          (with-exception-context {:argument arg-name}
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
                                                 e))))))]
      (let [dynamic-args (reduce-kv (fn [m k v]
                                      (assoc m k (process-arg k v)))
                                    nil
                                    arguments)]
        ;; This is kind of a juxt buried in a map. Each value is a function that accepts
        ;; the variables and returns the actual value to use.
        (fn [variables]
          (map-vals #(% variables) dynamic-args))))))

(defn ^:private disj*
  [set ks]
  (apply disj set ks))

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
                         (as-> $ (filter-vals non-null-kind? $))
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
  [selection query-path]
  {:query-path query-path
   :location (meta selection)})

(defn ^:private node-context
  [node-map]
  {:query-path (:query-path node-map)
   :locations [(:location node-map)]})

(defn ^:private convert-directives
  "Passed a container (a field selection, etc.) containing a :directives key,
  processes each of the directives: validates that the directive exists,
  and validates the arguments of the directive.

  Returns an updates container."
  [schema directives]
  (let [convert-directive
        (fn [k directive]
          (with-exception-context {:directive k}
            (if-let [directive-def (get builtin-directives k)]
              (let [[literal-arguments dynamic-arguments-extractor]
                    (try
                      (process-arguments schema
                                         (:args directive-def)
                                         (:arguments directive))
                      (catch ExceptionInfo e
                        (throw-exception (format "Exception applying arguments to directive %s: %s"
                                                 (q k)
                                                 (to-message e))
                                         nil
                                         e)))]
                (assoc directive
                       :arguments literal-arguments
                       ::arguments-extractor dynamic-arguments-extractor))
              (throw-exception (format "Unknown directive %s."
                                       (q k)
                                       {:unknown-directive k
                                        :available-directives (-> builtin-directives keys sort)})))))
        reducer (fn [m k v]
                  (assoc m k (convert-directive k v)))]
    (reduce-kv reducer nil directives)))

(defn ^:private convert-field-selection
  "Converts a parsed field selection into a normalized form, ready for validation
  and execution.

  Returns a tuple of the type of the field (used when resolving sub-types)
  and a reduced and an enhanced version of the selection map."
  [schema selection type query-path]
  (let [defaults (default-node-map selection query-path)
        context (node-context defaults)
        result (with-exception-context context
                 (reduce node-reducer defaults (rest (second selection))))
        {:keys [field alias arguments reportable-arguments directives]} result
        field-definition (get-in type [:fields field])
        field-type (schema/root-type-name field-definition)
        nested-type (get schema field-type)
        query-path' (conj query-path field)]
    (with-exception-context (assoc context :field field)
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
        [nested-type (assoc result
                            :selection-type :field
                            :directives (convert-directives schema directives)
                            :alias (or alias field)
                            :query-path query-path'
                            :leaf? (scalar? nested-type)
                            :concrete-type? (-> type :category #{:object :input-object} some?)
                            :reportable-arguments reportable-arguments
                            :arguments literal-arguments
                            ::arguments-extractor dynamic-arguments-extractor
                            :field-definition field-definition)]))))

(defn ^:private select-operation
  "Given a collection of operation definitions and an operation name (which
  might be nil), retrieve the requested operation definition from the document."
  ;; operations is a seq of operation definitions, each like:
  ;; [:operationDefinition
  ;;   [:operationType ...]
  ;;   [:name <string>]
  ;;   [:variableDefinitions ...]
  ;;   [:directives ...]
  ;;   [:selectionSet ...]
  [operations operation-name]
  (cond-let
    :let [operation-count (count operations)
          single-op? (= 1 operation-count)
          first-op (first operations)]

    (and single-op?
         operation-name
         (or (not (< 2 (count (second first-op))))
             (not= operation-name
                   (-> first-op second (nth 2) second))))

    (throw-exception "Single operation did not provide a matching name."
                     {:op-name operation-name})

    single-op?
    first-op

    :let [operation (some #(when (= operation-name
                                    ;; We can only check named documents
                                    (and (< 2 (count (second %)))
                                         (-> % second (nth 2) second)))
                             %)
                          operations)]

    (not operation)
    (throw-exception "Multiple operations requested but operation-name not found."
                     {:op-count operation-count
                      :operation-name operation-name})

    :else operation))

(defn ^:private descend-to-selection-set
  "For the top-level of the parse-tree, we need to descend to the first
  requested selection-set for the operation."
  [operation]
  (if (and (sequential? (last operation))
           (= :selectionSet (first (last operation))))
    (last operation)
    (recur (last operation))))

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
  (reduce-kv (fn [node directive-type directive]
               (let [effector (get-in builtin-directives [directive-type :effector])]
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


(defn ^:private normalize-selections
  "Starting with a selection (a field or fragment) recursively normalize any nested selections selections,
  and handle marking the node for any necessary prepare phase operations."
  [schema m type query-path]
  (-> m
      (update? :selections
               (fn [sub-selections]
                 ;; Strict evaluation, since exceptions may be thrown:
                 (mapv #(selection schema % type query-path) sub-selections)))
      mark-node-for-prepare))

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
  [schema _type fragment-definitions]
  (let [f (fn [def]
            (let [defaults {:location (meta def)}
                  m (reduce node-reducer defaults (rest def))
                  type-name (:type m)
                  path-elem (keyword (-> m :fragment-name name)
                                     (name type-name))
                  fragment-type (get schema type-name)]
              (normalize-selections schema
                                    m
                                    fragment-type
                                    [path-elem])))]
    (into {} (comp (map f)
                   (map (juxt :fragment-name
                              #(finalize-fragment-def schema %))))
          fragment-definitions)))

(defmulti ^:private selection
  "A recursive function that parses the ANTLR selection structure into the
   format used during execution; this involves tracking the current schema type
   (initially, nil) and query path (which is used for error reporting)."
  (fn [_schema sel _type _q-path]
    ;; e.g. [:selection [:field ...]] --> :field
    (-> sel second first)))

(defmethod selection :field
  [schema sel type q-path]
  (let [[nested-type m] (convert-field-selection schema sel type q-path)]
    (normalize-selections schema m nested-type (:query-path m))))

(defmethod selection :inlineFragment
  [schema sel _type q-path]
  (let [defaults (default-node-map sel q-path)]
    (with-exception-context (node-context defaults)
      (let [m (reduce node-reducer defaults (rest (second sel)))
            type-name (:type m)
            fragment-type (get schema type-name)]
        (if (nil? fragment-type)
          (throw-exception (format "Inline fragment has a type condition on unknown type %s."
                                   (q type-name)))
          (let [concrete-types (expand-fragment-type-to-concrete-types fragment-type)
                fragment-path-term (keyword "..." (name type-name))
                inline-fragment (-> m
                                    (assoc :selection-type :inline-fragment
                                           :concrete-types concrete-types)
                                    (update :directives #(convert-directives schema %)))]
            (normalize-selections schema
                                  inline-fragment
                                  fragment-type
                                  (-> m :query-path (conj fragment-path-term)))))))))

(defmethod selection :fragmentSpread
  [schema sel _type q-path]
  (let [defaults (default-node-map sel q-path)
        m (with-exception-context (node-context defaults)
            (reduce node-reducer defaults (rest (second sel))))]
    (-> m
        (assoc :selection-type :fragment-spread)
        (update :directives #(convert-directives schema %))
        mark-node-for-prepare)))

(defn ^:private find-element [container element-type]
  (->> container
       next
       (filter #(= (first %) element-type))
       first))

(defn ^:private element->map
  "Maps a parsed element to a map."
  [element]
  (reduce (fn [m sub-element]
            (assoc m (first sub-element) (rest sub-element)))
          nil
          (rest element)))

(defn ^:private construct-var-type-map
  [parsed]
  (let [[type value] parsed]
    (case type
      :typeName
      {:kind :root
       :type (-> value second keyword)}

      :nonNullType
      {:kind :non-null
       :type (construct-var-type-map value)}

      :listType
      {:kind :list
       :type (-> value second construct-var-type-map)}

      (throw-exception "Unable to parse variable type."))))

(defn ^:private compose-variable-definition
  "Converts a variable definition into a tuple of variable name, and
  schema-type like an argument definition."
  [schema variable-definition]
  (let [m (element->map variable-definition)
        var-name (-> m :variable first second keyword)
        var-def {:type (-> m :type first construct-var-type-map)
                 :var-name var-name}
        ;; Simulate a field definition around the raw type:
        type-name (schema/root-type-name var-def)
        schema-type (get schema type-name)
        ;; eg. [:stringvalue "\"fred\""]
        default-value (-> m :defaultValue first second)]
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
  (when-let [var-definitions (find-element operation :variableDefinitions)]
    (into {}
          (map #(compose-variable-definition schema %)
               (rest var-definitions)))))


(defn ^:private xform-query
  "Given an output tree of sexps from clj-antlr, traverses and reforms into a
  form expected by the executor."
  [schema antlr-tree operation-name]
  (let [{:keys [fragmentDefinition operationDefinition]}
        (group-by first (map second (rest antlr-tree)))

        operation
        (select-operation operationDefinition operation-name)

        op-type (find-element operation :operationType)

        ;; Can only be a mutation if the leading keyword is provided as is "mutation".
        mutation? (and op-type
                       (-> op-type
                           second
                           second
                           (= "mutation")))

        root-key (if mutation?
                   constants/mutation-root
                   constants/query-root)

        root (get schema root-key)

        variable-definitions (extract-variable-definitions schema operation)

        selections
        (descend-to-selection-set operation)

        ;; Clumsy but necessary way to let lower levels know about variable definitions.
        ;; This will deviate from the spec slightly: all fragments will be transformed
        ;; and validated using the variables from the selected operation, even those that
        ;; are not referenced by the selected operation (another operation may define
        ;; different variables). A solution might be to collect up the fragments that
        ;; are referenced inside the operation, validate those, discard the rest.
        schema' (assoc schema ::variables variable-definitions)]
    ;; Build the result describing the fragments and selections (or the selected operation).
    ;; Explicitly defeat some lazy evaulation, to ensure that validation exceptions are thrown
    ;; from within this function call.
    {:fragments (normalize-fragment-definitions schema' nil fragmentDefinition)
     :selections (mapv #(selection schema' % root [])
                       (rest selections))
     :mutation? mutation?
     constants/schema-key schema}))

(defn ^:private parse-failures
  [^ParseError e]
  (let [errors (deref e)]
    (map (fn [{:keys [line column message]}]
           {:location {:line line
                       :column column}
            :parse-error message})
         errors)))

(defn ^:private antlr-parse
  "Parse the query, then traverse it into nested Clojure data."
  [query-string]
  (let [{:keys [tree parser]} (antlr.proto/parse grammar nil query-string)]
    (traverse tree parser)))

(defn prepare-with-query-variables
  "Given a parsed query data structure and a map of variables,
  update the query, calculating field arguments and applying directives."
  [parsed-query variables]
  (let [prepare #(prepare-node % variables)]
    (-> (prepare-nested-selections parsed-query variables)
        (update :fragments #(map-vals prepare %)))))

(defn parse-query
  "Given a compiled schema and a query-string, parses it using the Antlr grammar.
  Returns an executable form of the query: a map of the form `{:fragments ... :selections ...}`."
  ([schema query-string]
   (parse-query schema query-string nil))
  ;; This version is rarely used: it assumes that document defines multiple named operations and only
  ;; one is being selected. With an eye towards fast execution of parsed and cached queries, this may
  ;; not be the right approach.
  ([schema query-string operation-name]
   (let [antlr-tree
         (try
           (antlr-parse query-string)
           (catch ParseError e
             (let [failures (parse-failures e)]
               (throw (ex-info "Failed to parse GraphQL query."
                               {:errors failures})))))]
     (xform-query schema antlr-tree operation-name))))
