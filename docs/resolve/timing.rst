Resolver Timing
===============

.. caution::

  This feature is experimental, and subject to change. Feedback is encouraged!


When scaling a service, it is invaluable to know where queries are spending their execution time; Lacinia
can help you here; when enabled, Lacinia will collect start and finish timestamps, and elapsed time, for each
resolver function that is invoked during execution of the query.

Timing collection is enabled using the key, ``:com.walmartlabs.lacinia/enable-timing?``, in the application context:

.. literalinclude:: /_examples/timings.edn
   :language: clojure

.. sidebar:: Extensions key?

   GraphQL supports a third result key, ``extensions``, as
   described in `the spec <https://facebook.github.io/graphql/#sec-Response-Format>`_.
   It exists just for this kind of extra information in the response.

Timings are returned in a tree structure below the ``:extensions`` key of the result.
The tree structure reflects the structure of the *query* (not the schema).

We can see here that the ``human`` query operation's resolver was invoked twice, and the ``friends`` field's
resolver was invoked just once.
Only explicitly added resolve functions are timed; default resolve functions are trivial and not included.

For each invocation, values are identified for the start and finish time (in standard format, milliseconds since the epoch),
and elapsed time (also in milliseconds).

Since our sample data is all in-memory, execution is instantaneous, so the elapsed time is 0.
In real applications, making requests to a database or accessing other resources, there would be some amount of elapsed time.

When the field resolvers are :doc:`asynchronous <async>`, you'll often see start and finish times for each
invocation overlap.  The finish time is calculated not when the resolver function returns, but
when it produces a value (that is, when the ResolverResult is realized).
