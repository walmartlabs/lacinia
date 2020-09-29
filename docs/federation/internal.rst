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
