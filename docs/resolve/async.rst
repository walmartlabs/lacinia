Asynchronous Field Resolvers
============================

Lacinia supports asynchronous field resolvers: resolvers that run in parallel
within a single request.

This can be very desirable: different fields within the same query
may operate on different databases or other backend data sources, for example.
Queries to these backends can easily execute in parallel, resulting in better
throughput for the overall GraphQL query execution than executing serially.

Alternately, a single request may invoke multiple top-level operations which, again,
can execute in parallel.

It's very easy to convert a normal synchronous field resolver into an
asynchronous field resolver:
Instead of returning a normal value, an asynchronous field resolver
returns a special kind of :doc:`ResolverResult <resolve-as>`, a
``ResolverResultPromise``.

Such a promise is created by the :api:`resolve/resolve-promise` function.

The field resolver function returns immediately, but will typically perform some work in a background
thread.
When the resolved value is ready, the ``deliver!`` method can be invoked on the promise.

.. literalinclude:: ../_examples/async-example.edn
   :language: clojure

The promise is created and returned from the
field resolver function.
In addition, as a side effect, a thread is started to perform some work.
When the work is complete, the ``deliver!`` method on the promise will inform
Lacinia, at which point Lacinia can start to execute selections on the resolved value
(in this example, the user data).

On normal queries, Lacinia will execute as much as it can in parallel.
This is controlled by how many of your field resolvers return a promise rather than
a direct result.

Despite the order of execution, Lacinia ensures that the order of keys in the result map
matches the order in the query.

.. sidebar:: GraphQL Spec

   Read about :spec:`execution <Normal-and-Serial-Execution>`.

For mutations, the top-level operations execute serially.
That is, Lacinia will execute one top-level operation entirely before
starting the next top-level operation.

Timeouts
--------

Lacinia does not enforce any timeouts on the field resolver functions, or the
promises they return.
If a field resolver fails to ``deliver!`` to a promise, then Lacinia will block,
indefinitely.

It's quite reasonable for a field resolver to enforce some kind of timeout on its own,
and deliver nil and an error message when a timeout occurs.

Exceptions
----------

Uncaught exceptions in an asynchonous resolver are especially problematic, as it means that
ResolverResultPromises are never delivered.

In the example above, any thrown exception is converted to an
:doc:`error map <resolve-as>`.

.. warning::

   Not catching exceptions will lead to promises that are never delivered and that
   will cause Lacinia to block indefinitely.

Thread Pools
------------

The ``on-deliver!`` callback does not execute on the main thread, it is always
invoked in a worker thread within a thread pool.

It is recommended that an application-specific ``ThreadPoolExecutor`` be provided as the
``:executor`` option when compiling the schema.  If omitted, then a default
executor will be provided.

.. note::

   The ``:executor`` option was added in Lacinia 1.2.

