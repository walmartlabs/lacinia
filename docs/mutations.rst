Mutations
=========

Mutations parallel :doc:`queries <queries>`, except that the root field resolvers may make
changes to underlying data in addition to exposing data.

The :doc:`field resolver <resolve/index>` for a mutation will, as with a query, be passed nil
as its value argument (the third argument).
A mutation is expected to perform some state changing operation, then return a value that
indicates the new state; this value will be recursively resolved and selected, just as with
a query.

Mutations are defined in the schema using the top-level ``:mutations`` key.

Mutations may also be defined as fields of the :doc:`root mutation object <roots>`.

When a single query includes more than one mutation, the mutations *must* execute in the client-specified
order.
This is different from queries, which allow for each root query to run in
:doc:`parallel <resolve/async>`.

Typically, mutations are only allowed when the incoming request is explicitly an HTTP POST.
However, that is beyond the scope of Lacinia (it doesn't know about the HTTP request, just
the query string extracted from the HTTP request).
