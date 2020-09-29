Apollo GraphQL Federation
=========================

GraphQL federation is a concept, spearheaded by
`Apollo GraphQL <https://www.apollographql.com/docs/apollo-server/federation/introduction/>`_ (a Node-based JavaScript project)
whereby multiple GraphQL schemas can be combined, behind a single `gateway` service.
It's a useful concept, as it allows different teams to stand up their own schemas and services, written in
any language, and dynamically combine them into a single "super" schema.

Each service's schema can evolve independentently (as long as that evolution is backwards compatible), and can deploy
on its own cycle. The gateway becomes the primary entrypoint for all clients, and it knows how to break
service-spanning queries apart and build an overall query plan.

Lacinia has been extended, starting in 0.38.0, to support acting as an implementing service; there is no plan
at this time to act as a gateway.

.. warning::

    At this time, only a schema defined with the :doc:`Schema Definition Language </schema/parsing>`, can be extended to act as
    a service implementation.

Essentially, federation allows a set of services to each provide their own types, queries, and mutations, and organizes things so that
each service can provide additional fields to the types provided by the other services.

The `Apollo GraphQL documentation <https://www.apollographql.com/docs/apollo-server/federation/introduction/#concern-based-separation>`_ includes
a basic example, where a users service exposes a ``User`` type (and related queries), a products service exposes
a ``Product`` type, and a reviews service exposes a ``Review`` type.

Without federation, these individual services are useful, but limited.
A smart client could know about all three services, and send a series of requests to each, to build 
up a model of, say, a particular user and the products that user has reviewed.

For example:

* Query the users service for the user, providing the user's unique id
* Query the reviews service to get a list of reviews for that specific user (again, passing the user's unique id)
* Query the products service for details (name, price, etc.) for each product reviewed by the user

... but this is a lot to heap on the client developers; each client will have to manage three sets of GraphQL endpoints, and know exactly
which fields are needed to bridge relationships between the different services.

Instead, federation allows the Apollo GraphQL gateway to merge together the three individual services into one composite service.
The client only needs access to the single gateway endpoint, and is free to make complex queries that
span from ``User`` to ``Review`` to ``Product`` seamlessly;
the gateway service is responsible for communicating with the implementing services and merging together the final
response.

A GraphQL type (or interface) that can span services this way is called an `entity`.

In federation terms, the ``User`` entity is `internal` to the users service, and `external` to the other two services.
The users service defines all the fields of the ``User`` entity, and can add new fields whenever necessary while staying
backwards compatible, just as with a traditional GraphQL schema.

In the other schemas, the ``User`` type is `external`; just a kind of stub for ``User`` is defined in the schemas for
the products and reviews service. The full type, and the stub, must agree on fields that define the primary key
for the entity. However, the stub can be extended by the other services to add new fields, surfacing data and relationships
owned by the particular service.

.. toctree::
    :hidden:

    internal
    external
    reps
    implementation
