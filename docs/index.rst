Lacinia - GraphQL for Clojure
=============================

.. remoteliteral::
  :uri: http://github.com/foo.html


Lacinia is a library for implementing
Facebook's
`GraphQL <https://facebook.github.io/react/blog/2015/05/01/graphql-introduction.html>`_
specification in idiomatic
`Clojure <http://clojure.org>`_.

GraphQL is a way for clients to efficiently obtain data from servers.

Compared to traditional REST approaches, GraphQL ensures that clients can access
exactly the data that they need (and no more), and do so with fewer round-trips to the server.

This is especially useful for mobile clients, where bandwidth is always
at a premium.

In addition, GraphQL is self describing; the shape of the data that
can be exposed, and the queries by which that data can be accessed,
are all accessible using GraphQL queries.
This allows for sophisticated, adaptable clients, such as the
in-browser GraphQL IDE `graphiql`_.

.. warning::

   This library is still under active development and should not be considered complete.
   If you would like to contribute, please create a pull request.

Although GraphQL is quite adept at handling requests from client web browsers and
responding with JSON, it is also exceptionally useful for allowing backend systems to communicate.


.. toctree::
   :hidden:

   overview
   fields
   objects
   interfaces
   enums
   unions
   queries
   mutations
   subscriptions/index
   directives
   resolve/index
   input-objects
   custom-scalars
   introspection
   schema/parsing
   samples
   clojure
   resources

   contributing

   API Documentation <http://walmartlabs.github.io/lacinia/>
   GitHub Project <https://github.com/walmartlabs/lacinia>



Using this library
==================

This library aims to maintain feature parity to that of the `official reference JavaScript implementation <https://github.com/graphql/graphql-js/>`_
and be fully compliant with the GraphQL specification.

Lacinia can be plugged into any Clojure HTTP pipeline.
The companion library `lacinia-pedestal <https://github.com/walmartlabs/lacinia-pedestal>`_ provides
full HTTP support, including
:doc:`GraphQL subscriptions <subscriptions/index>`,
for `Pedestal <https://github.com/pedestal/pedestal>`_.


Overview
--------

A GraphQL server starts with a schema of exposed types.

This GraphQL schema is described as an `EDN <https://github.com/edn-format/edn>`_ data structure:

.. literalinclude:: ../dev-resources/star-wars-schema.edn
  :language: clojure


The schema defines all the data that could possibly be queried by a client.

To make this schema useful, :doc:`field resolvers <resolve/index>` must be added to it.
These functions are responsible for doing the real work
(querying databases, communicating with other servers, and so forth).
These are attached to the schema after it is read from an EDN file, using
the placeholder keywords in the schema, such as ``:resolve :droid``.

The client uses the GraphQL query language to specify exactly what data
should be returned in the result map::


   {
     hero {
       id
       name
       friends {
         name
       }
     }
   }

This translates to "run the hero query; return the default hero's id and name, and friends; just return the name of each
friend."

Lacinia will return this as Clojure data:

.. literalinclude:: _examples/hero-query-response.edn
   :language: clojure

This is because R2-D2 is, of course, considered the hero of
the Star Wars trilogy.

This Clojure data can be trivially converted into JSON or other formats when Lacinia is used
as part of an HTTP server application.

A key takeaway: GraphQL is a contract between a client and a server; it doesn't know or care where
the data comes from; that's the province of the field resolvers.
That's great news: it means Lacinia is equally adept at pulling data out of a single database
as it is at integrating and organizing data from multiple backend systems.

.. _graphiql:

https://github.com/graphql/graphiql
