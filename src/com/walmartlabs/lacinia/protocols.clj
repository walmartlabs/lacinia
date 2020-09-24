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

(ns com.walmartlabs.lacinia.protocols
  "Protocols for objects accessible from the [[selection]] function, "
  {:added "0.38.0"})

(defprotocol QualifiedName
  (qualified-name [named]
    "Returns a keyword whose namespace is the containing element; e.g., :User/id for the id field of the User type."))

(defprotocol Arguments

  "Implemented by [[Directive]]."

  (arguments [element]
    "Returns a map of keyword name to value.  May return nil if no arguments.

     The value may reflect query variables or argument defaults."))

(defprotocol Directive

  "Implements [[Arguments]]."

  (directive-type [d]
    "Returns the type of directive as a keyword."))

(defprotocol Directives
  "An element that may contain directives."

  (directives [element]
    "Returns a map of directives for this element; keys are keywords, values are a seq of Directive.

    May return nil."))

(defprotocol SelectionSet

  "An selection that may contain sub-selections."

  (selection-kind [selection]
    "The type of selection: either :field, :inline-fragment or :named-fragment.

    For :field, the [[FieldSelection]] protocol will also be implemented.")

  (selections [selection]
    "Returns a seq of sub-selections (also SelectionSets) of this selection.

    May be nil for a field that selects a scalar."))

(defprotocol FieldSelection

  "A SelectionSet that extracts a value from a field and records it into the
   result as a name or alias; for non-scalar types, may have sub-selections."

  (field-name [fs]
    "Returns the name of the field (as an unqualified keyword).")

  (root-value-type [fs]
    "Returns the root value type for this field (the actual type may
    include `list` or `non-null` qualifiers).")

  (alias-name [fs]
    "Returns the alias for the field selection, or name of the field."))

(defprotocol Type

  "A type defined in a GraphQL schema.  Implements the [[Directives]] protocol as well."

  (type-name [type]
    "Returns the name of the type, as a keyword.")

  (type-kind [type]
    "Returns the kind of type, one of: `:object`, `:union`, `:interface`, `:scalar`, or `:enum`."))


