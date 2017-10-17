Unions
======

A union type is a type that may be any of a list of possible objects.

.. sidebar:: GraphQL Spec

   Read about :spec:`unions <Unions>`.

A union is a type defined in terms of different objects:

.. literalinclude:: _examples/union-definition.edn
   :language: clojure

A union definition must include a ``:members`` key, a sequence of object types.

The above example identifies the ``:search-result`` type to be either a ``:person`` (with fields
``:name`` and ``:age``), or a ``:photo`` (with fields ``:imageURL``, ``:title``, ``:height``, and ``:width``).

Unions must define at least one type; each member type must be an object type (they may not reference scalar types,
interfaces, or other unions).

When a client makes a union request, they must use the fragment spread syntax to identify what
is to be returned based on the runtime type of object:

.. code-block:: js

   { search (term:"ewok") {
     ... on person { name }
     ... on photo { imageURL title }
   }}

This breaks down what will be returned in the result map based on the type of the value produced
by the ``:search`` query.  Sometimes there will be a ``:name`` key in the result, and other times
an ``:image-url`` and ``:title`` key.
This may vary result by result even within a single request:

.. literalinclude:: _examples/unions-query-response.edn
   :language: clojure

.. tip::

   When a field or operation type is a union,
   the field resolver may return any of a number of different
   concrete object types, and Lacinia has no way to determine which;
   this information must be :doc:`explicitly provided <resolve/type-tags>`.
