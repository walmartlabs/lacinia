Internal Entities
=================

Defining an entity that is internal is quite straight-forward, it is almost the same as in
traditional GraphQL.

.. sidebar:: Examples

   Here, and in the remaining examples, we've simplified the example from
   Apollo GraphQL's documentation to just two services:
   users and products.

.. literalinclude:: /_examples/fed/internal.gql

When federation is enabled, a 
`number of new directives <https://www.apollographql.com/docs/apollo-server/federation/federation-spec/#key>`_ are automatically available, 
including ``@key``, which defines the primary key, or primary keys, for the entity.

The above example would be the schema for a users service that can be extended by other services.

.. warning::

   Lacinia's default name for the object containing queries is ``QueryRoot``, whereas the default for Apollo and 
   `most other GraphQL libraries <https://graphql.org/learn/schema/#the-query-and-mutation-types>`_ is ``Query``.  

   Because of this, and because `Apollo doesn't do the right thing here <https://github.com/apollographql/apollo-server/issues/4554>`_,
   it is necessary to override Lacinia's default by adding ``schema { query : Query }``.

   The same holds true for mutations, which must be on a type named ``Mutation``.
