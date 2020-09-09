External Entities
=================

The previous example showed an internal entity that can be extended; this example shows a different service providing
its own internal entity, but also extending the ``User`` entity.

.. literalinclude:: /_examples/fed/external.gql

Note the use of the ``@extends`` directive, this indicates that ``User`` (in the products service) is a stub for the full
``User`` entity in the users service.

You must ensure that the external ``User`` includes the same ``@key`` directive (or directives), and the same primary key
fields; here ``id`` must be present, since it is part of the primary key.
The ``@external`` directive indicates that the field is provided by another service (the users service).

The ``favoriteProducts`` field on ``User`` is an addition provided by this service, the products service.
Like any other field, a resolver must be provided for it.
We'll see how that works shortly.

Notice that this service adds the ``productByUpc`` query to the ``Query`` object; the Apollo GraphQL gateway
merges together all the queries defined by all the implementing services.

Again, the point of the gateway is that it mixes and matches from all the implementing services; clients should
*only* go through the gateway since that's the only place where this merged view of all the individual schemas
exists.

The gateway is capable of building a plan that involves multiple steps to satisfy a client query.

For example, consider this gateway query:

.. literalinclude:: /_examples/fed/query.gql

The gateway will start with a query to the users service; it will invoke the ``userById`` query there and will
select both the ``name`` field (as specified in the client query) and the ``id`` field (since that's specified
in the ``@key`` directive on the ``User`` entity).

A second query will be sent to the products service.
This query is used to get those favorite products; but to understand exactly
how that works, we must first discuss `representations`.