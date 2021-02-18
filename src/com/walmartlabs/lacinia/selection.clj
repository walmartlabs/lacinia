;; Copyright (c) 2020-present Walmart, Inc.
;;
;; Licensed under the Apache License, Version 2.0 (the "License")
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns com.walmartlabs.lacinia.selection
  "Protocols for selection-related objects accessible from the [[selection]] function, and
   as well as the schema type objects navigable to from the selection."
  {:added "0.38.0"})

(defprotocol QualifiedName
  (qualified-name [named]
    "Returns a keyword whose namespace is the containing element; e.g., :User/id for the id field of the User type."))

(defprotocol Arguments

  "Implemented by [[Directive]]."

  (arguments [element]
    "Returns a map of keyword name to argument value.  May return nil if no arguments.

     The value may reflect query variables or argument defaults."))

(defprotocol ArgumentDefs

  "Definition of arguments to a [[Field]]."

  (argument-defs [element]
    "Returns a map of keyword name to [[Argument]], or nil."))

(defprotocol Argument
  "An argument definition, implements [[TypeDef]] and [[QualifiedName]].")

(defprotocol Directive

  "Implements [[Arguments]]."

  (directive-type [d]
    "Returns the type of directive as a keyword."))

(defprotocol Directives
  "An element that may contain directives."

  (directives [element]
    "Returns a map of directives for this element; keys are keywords identifying
    the directive, values are a seq of [[Directive]] of that type (directives
    are inherently repeatable).

    May return nil."))

(defprotocol SelectionSet

  "A selection that may contain sub-selections."

  (selection-kind [selection]
    "The type of selection: either :field, :inline-fragment or :named-fragment.

    For :field, the [[FieldSelection]] protocol will also be implemented.")

  (selections [selection]
    "Returns a seq of sub-selections (also SelectionSets) of this selection.

    May be nil for a field that selects a scalar."))

(defprotocol FieldSelection

  "A [[SelectionSet]] that extracts a value from a field and records it into the
   result as a name or alias; for non-scalar types, may have sub-selections.

   Also implements [[QualifiedName]], [[Arguments]], and [[Directives]].

   Directives here are the directives on the selection (the executable directives);
   access the underlying field to get the type system directives."

  (field-name [fs]
    "Returns the name of the field (as an unqualified keyword).")

  (field [fs]
    "Returns the field actually selected, a [[Field]].")

  (root-value-type [fs]
    "Returns the root value [[SchemaType]] for this field (the actual type may
    include `list` or `non-null` qualifiers).")

  (alias-name [fs]
    "Returns the alias for the field selection, or name of the field."))

(defprotocol SchemaType

  "A type defined in a GraphQL schema.  Implements the [[Directives]] protocol as well."

  (type-name [type]
    "Returns the name of the type, as a keyword.")

  (type-category [type]
    "Returns the category of the type, one of: `:object`, `:union`, `:interface`, `:scalar`, or `:enum`."))

(defprotocol Fields

  "Implemented by the :object and :interface [[SchemaType]] kinds to expose the type's fields."

  (fields [type]
    "Returns a map of keyword to [[Field]]."))

(defprotocol Type

  "For a typed element, such as a [[Field]] or an [[ArgumentDef]], details the
  schema type."

  (kind [element]
    "The [[Kind]] of the element.")

  (root-type [element]
    "Returns the root [[SchemaType]] of the element.")

  (root-type-name [element]
    "Returns the keyword name of root type of the element."))

(defprotocol Kind
  "A Kind is a root type with qualifiers (list, or not-null). A root kind identifies a schema [[Type]].
  A Kind can be converted to an GraphQL."

  (kind-type [kind]
    "One of :non-null, :list, or :root.")

  (as-type-string [kind]
    "Returns the kind as an GraphQL language string, e.g. `[String!]`.")

  (of-kind [kind]
    "Returns the nested [[Kind]], or nil if a root kind.")

  (of-type [kind]
    "Returns the root [[Type]], or nil if not a root kind."))

(defprotocol Field

  "A field within a [[SchemaType]].  Implements [[Type]], [[Directives]], [[Arguments]], and [[QualifiedName]].")



