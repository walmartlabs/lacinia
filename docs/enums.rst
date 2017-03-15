Enums
=====

.. sidebar:: GraphQL Spec

   Read about `enums <https://facebook.github.io/graphql/#sec-Enums>`_.

GraphQL supports enumerated types: enums are effectively a string type, where the possible
values are restricted to a predefined set.

.. literalinclude:: _examples/enum-definition.edn
   :language: clojure

As with many other elements in GraphQL, a description may be provided (for use with
:doc:`introspection`).


