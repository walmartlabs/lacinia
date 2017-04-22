Overview
========

GraphQL consists of two main parts:

* A server-side *schema* that defines the available queries and types of data that may be returned.

* A client query language that allows the client to specify what query to execute, and what data
  to return.

The `GraphQL specification <https://facebook.github.io/graphql>`_ goes into detail about the
format of the client query language, and the expected behavior of the server.

This library, Lacinia, is an implementation of the key component of the server,
in idiomatic Clojure.

The GraphQL specification includes a language to define the server-side schema; the
``type`` keyword is used to introduce a new kind of object.

In Lacinia, the schema is Clojure data: a map of keys and values; top level
keys indicate the type of data being defined:

.. literalinclude:: ../dev-resources/star-wars-schema.edn
   :language: clojure

Here, we are defining *human* and *droid* objects.
These have a lot in common, so we define a shared *character* interface.

But how to access that data?  That's accomplished using one of three queries:

* hero

* human

* droid

In this example, each query returns a single instance of the matching object.
Often, a query will return a list of matching objects.

Using the API
-------------

The schema starts as a data structure, we need to add in the field resolver and then *compile* the result.

.. literalinclude:: ../dev-resources/org/example/schema.clj
    :language: clojure

Compilation performs a number of checks, applies defaults, merges in introspection data about the schema,
and performs a number of other operations to ready the schema for use.

With that in place, we can now execute queries.

.. literalinclude:: _examples/overview-exec-query.edn
   :language: clojure

.. sidebar:: Ordered Map?

   The `#ordered/map` indicates that the fields in the response are returned in the
   `same order` [#order]_ as they are specified in the query document.

   In most examples, for conciseness, a standard (unordered) map is shown.

The query string is parsed and matched against the queries defined in the schema.

The two nils are variables to be used executing the query, and an application context.

In GraphQL, queries can pass arguments (such as ``id``) and queries identify
exactly which fields
of the matching objects are to be returned.
This query can be stated as `just provide the name of the human with id '1001'`.

This is a successful query, it returns a map with a ``:data`` key.
A failed query would return a map with an ``:errors`` key.
A query can even be partially successful, returning as much data as it can, but also errors.

Inside ``:data`` is a key corresponding to the query, ``:human``, whose value is the single
matching human.  Other queries might return a list of matches.
Since we requested just a slice of a full human object, just the human's name, the map has just a single
``:name`` key.

.. [#order] This shouldn't be strictly necessary (JSON and EDN don't normally care about key order, and
   keys can appear in arbitrary order),
   but having consistent ordering makes writing tests involving GraphQL queries easier: you can
   typically check the textual, not parsed, version of the response directly against an expected string value.
