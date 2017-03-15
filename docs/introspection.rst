Introspection
=============

Introspection is a key part of GraphQL: the schema is self-describing.

.. sidebar:: GraphQL Spec

   Read about `introspection <https://facebook.github.io/graphql/#sec-Introspection>`_.

Introspection data is derived directly from the schema.
Often, a ``:description`` key is added to the schema to provide additional help.

Introspection is necessary to support the in-browser `graphiql`_ IDE.

Introspection can also be leveraged by smart clients, presumably custom in-browser or mobile applications,
to help deal with schema evolution.
A smart client can use introspection to determine if a particular field exists before
including that field in a query request; this can help defuse the process of introducing
a new field (in the server) at the same time as a new client that needs to make use of that field.


.. _graphiql: https://github.com/graphql/graphiql




