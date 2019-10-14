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
   described in :spec:`the spec <Response-Format>`.
   It exists just for this kind of extra information in the response.

The ``:timings`` extension key is a list of timing records.

When the field resolvers are :doc:`asynchronous <async>`, you'll often see start and finish times for each
invocation overlap.  The finish time is calculated not when the resolver function returns, but
when it produces a value (that is, when the ResolverResult is realized).

By carefully looking at the start and end times, we can see that
the ``luke`` and ``leia`` fields ran simultaneously, and that
the ``friends`` field (under ``luke``) executed only after ``luke`` completed.

Although the total elapsed time across the three fields was 62 ms, some of that time overlapped, and the overall
request processing time was approximately 51ms.

Timing information is only collected for fields with an explicit field resolver,
and when the elapsed time is less than 2ms, the tracking data is discarded.
This allows you to focus on non-trivial resolvers.

The start and finish times are in a standard format, milliseconds since the epoch.

As with errors and warnings, the path reflects the query path, using aliases provided
by the client query when appropriate.

Generally, timing records are added to the list in order of completion, but the exact order is
not guaranteed.

