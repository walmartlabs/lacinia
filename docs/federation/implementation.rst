Service Implementation
======================

At this point, we've discussed what goes into each implementing service's schema, and a bit about how
each service is responsible for resolving representations; let's finally see how this all fits together with
Lacinia.

Below is a sketch of how this comes together in the products service:

.. literalinclude:: /_examples/fed/products.edn

The ``resolve-users-external`` function is used to convert a seq of ``User`` representations
into a seq of ``User`` entity stubs; this is called from the resolver for the ``_entities`` query whose type
is a list of the ``_Entities`` union, therefore each value :doc:`must be tagged </resolve/type-tags>` with the ``:User`` type.

.. sidebar:: Return type

    Return type here is just like a normal field resolver that returns GraphQL list; it may be seq of values, or a
    :doc:`ResolverResult </resolve/resolve-as>` that delivers such a seq.

``resolve-products-internal`` does the same for ``Product`` representations, but since this is the
products service, the expected behavior is to perform a query against an external data store and
ensure the results match the structure of the ``Product`` entity.

``resolve-product-by-upc`` is the resolver function for the ``productByUpc`` query.
Since the field type is ``Product`` there's no need to tag the value.

``resolve-favorite-products`` is the resolver function for the ``User/favoriteProducts`` field.
This is passed the ``User`` (provided by ``resolve-users-external``); it extracts the ``id`` and passes
it to ``get-favorite-products-for-user``.

The remainder is bare-bones scaffolding to read, parse, and compile the schema and build a Pedestal
service endpoint around it.

Pay careful attention to the call to :api:`parser-schema/parse-schema`; the presence of the
``:federation`` option is critical; this adds the necessary base types and directives
before parsing the schema definition, and then adds the ``_entities`` query and ``_Entities`` union
afterwards, among other things.

The ``:entity-resolvers`` map is critical; this maps from a type name to a entity resolver;
this information is used to build the field resolver function for the ``_entities`` query.

.. warning::

    A lot of details are left out of this, such as initializing the database and storing
    the database connection into the application context, where functions like
    ``get-product-by-upc`` can access it.  
    
    This is only a sketch to help you connect the dots.