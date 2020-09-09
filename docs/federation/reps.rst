Representations
===============

A `representation` is a map that can be transferred from one implementing service to another, within the same federation.
This is necessary to allow work started in one service to continue in another; consider the query:

.. literalinclude:: /_examples/fed/query.gql

The gateway will query the ``User/favoriteProducts`` field on the products service as the second step on this query ... but
where does the ``User`` come from?

After the gateway performs the initial query on the users service, it builds a representation of the specific ``User``
to pass to the products service, using information from the ``@key`` directive:

.. code-block:: json

    {"__typename": "User",
     "id": "124c41"}

This representation is JSON, and is be passed to an implementing service's ``_entities`` query, which is automaticaly added
to the implementing service's schema by Lacinia:

.. literalinclude:: /_examples/fed/schema.gql

The ``_Entity`` union will contain all entities, internal or external, in the local schema; for the products service, this
will be ``User`` and ``Product``.

The ``_entities`` query exists to convert some number of representations (here, as scalar type ``_Any``) into entities
(either stub entities or full entities).
The gateway sends a request that passes the representations in, and uses fragments to extract the data needed
by the original client query::

    query($representations:[_Any!]!) {
        _entities(representations:$representations) {
            ... on User {
                favoriteProducts {upc name price}
            }
        }
    }

So, in the products service, the ``_entities`` resolver converts the representation into a stub ``User`` object,
containing just enough information so that the ``favoriteProducts`` resolver can perform whatever database query
it uses.
The response from the products service is merged together with the response from the users service and a final
response can be returned to the gateway service's client.

