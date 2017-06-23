Field Resolvers
===============

Field resolvers are how Lacinia goes beyond data modelling to actually providing access to data.

.. sidebar:: GraphQL Spec

   Read about `value resolution <https://facebook.github.io/graphql/#sec-Value-Resolution>`_.

Field resolvers are attached to fields, but also to top-level query and mutation operations.
It is only inside field resolvers that a Lacinia application can connect to a database or
an external system: field resolvers are where the data actually *comes* from.

In essence, the top-level operations perform the initial work in a request, getting the root
object (or collection of objects).

Field resolvers in nested fields are responsible for extracting and transforming that root data.
In some cases, a field resolver may need to perform additional queries against a back-end data store.


.. toctree::
    :hidden:

    overview
    attach
    type-tags
    exceptions
    resolve-as
    async
    selections
    timing
    examples
