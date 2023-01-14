Queries
=======

.. sidebar:: GraphQL Spec

   Read about :spec:`operations <Executing-Operations>`.

Queries are responsible for generating the initial resolved values that will be
picked apart to form the result map.

Other than that, queries are just the same as any other field.
Queries have a type, and accept arguments.

Queries are defined as the fields of a special object, :doc:`Query object <roots>`.

.. literalinclude:: _examples/query-def-var.edn
   :language: clojure

The :doc:`field resolver <resolve/index>` for a query is passed nil
as the the value (the third parameter).
Outside of this, the query field resolver is the same as any field resolver
anywhere else in the schema.

In the GraphQL specification, it is noted that queries are idempotent; if
the query document includes multiple queries, they are allowed to execute
in :doc:`parallel <resolve/async>`.


:queries key
------------

The above is the "modern" way to define queries; an older approach is still supported.
Queries may instead be defined using the ``:queries`` key of the schema.

.. literalinclude:: _examples/query-def.edn
   :language: clojure
