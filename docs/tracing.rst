Resolver Tracing
================

When scaling a service, it is invaluable to know where queries are spending their execution time; Lacinia
can help you here; when enabled, Lacinia will collect  performance tracing
information compatible with `Apollo GraphQL <https://github.com/apollographql/apollo-tracing/blob/master/README.md>`_.

Timing collection is enabled by passing the context through the
:api:`tracing/enable-tracing`
function:

.. literalinclude:: /_examples/tracing.edn
   :language: clojure

.. sidebar:: Extensions key?

   GraphQL supports a third result key, ``extensions``, as
   described in :spec:`the spec <Response-Format>`.
   It exists just for this kind of extra information in the response.

Note that tracing is an `execution` option, not a `schema compilation` option; it's just a matter of setting
up the application context (via ``enable-tracing``) before parsing and executing the query.

When the field resolvers are :doc:`asynchronous <resolve/async>`, you'll often see that the ``startOffset`` and ``duration``
of multiple fields represent overlapping time periods, which is exactly what you want.

Generally, resolver tracing maps are added to the list in order of completion, but the exact order is
not guaranteed.

Enabling tracing adds a lot of overhead to execution and parsing, both for the essential overhead of collecting
the tracing information, but also because certain optimizations are disabled when tracing is enabled;
for example default field resolvers (where no explicit resolver is provided) are heavily optimized normally, to
avoid unecessary function calls and object creation.

.. warning::

   Tracing should never been enabled in production.




