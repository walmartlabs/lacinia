Enums
=====

.. sidebar:: GraphQL Spec

   Read about `enums <https://facebook.github.io/graphql/#sec-Enums>`_.

GraphQL supports enumerated types: enums are effectively a string type, where the possible
values are restricted to a predefined set.

.. literalinclude:: _examples/enum-definition.edn
   :language: clojure

It is allowed to define enum values as either strings, keywords, or symbols.
Internally, the enum values are converted to strings.
Keyword values must be unique, otherwise an exception is thrown when compiling the schema.

When an enum type is used as an argument, the value provided to the field resolver function
will be a string, regardless of whether the enum was defined using strings, keywords, or symbols.

As with many other elements in GraphQL, a description may be provided (for use with
:doc:`introspection`).


