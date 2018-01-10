Enums
=====

.. sidebar:: GraphQL Spec

   Read about :spec:`enums <Enums>`.

GraphQL supports enumerated types, types whose value is limited to a explicit list.

.. literalinclude:: _examples/enum-definition.edn
   :language: clojure

It is allowed to define enum values as either strings, keywords, or symbols.
Internally, the enum values are converted to keywords.

Enum values must be unique, otherwise an exception is thrown when compiling the schema.

Enum values must be GraphQL Names: they may contain only letters, numbers, and underscores.

Enums `are` case sensitive; by convention they are in all upper-case.

When an enum type is used as an argument, the value provided to the field resolver function
will be a keyword, regardless of whether the enum values were defined using strings, keywords, or symbols.

Field resolvers are required to return a keyword, and that keyword must match one of the values in the enum.

As with many other elements in GraphQL, a description may be provided for the enum (for use with
:doc:`introspection`).

To provide a description for individual enum values, a different form must be used:

.. literalinclude:: _examples/enum-definition-description.edn
   :language: clojure

The ``:description`` key is optional.
You can mix-and-match the two forms.
