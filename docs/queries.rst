Queries
=======

.. sidebar:: GraphQL Spec

   Read about `operations <https://facebook.github.io/graphql/#sec-Executing-Operations>`_.

Queries are responsible for generating the initial resolved values that will be
picked apart to form the response.

Other than that, queries are just the same as any other field.
Queries have a type, and accept arguments.

Queries are defined using the ``:queries`` key of the schema.

.. literalinclude:: _examples/query-def.edn
   :language: clojure

Internally, each query becomes a field of a special object named ``:QueryRoot``.

The :doc:`field resolver <resolve/index>` for a query is passed nil
as the the value (the third parameter).
Outside of this, the query field resolver is the same as any field resolver
anywhere else.

In the GraphQL specification, it is noted that queries are idempotent; if
the query document includes multiple queries, they are allowed to execute
in :doc:`parallel <resolve/async>`.
