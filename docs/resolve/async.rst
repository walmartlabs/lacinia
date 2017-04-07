Asynchronous Field Resolvers
============================

Lacinia supports asynchronous field resolvers: resolvers that run in parallel
within a single request.

This can be very desirable: different fields within the same query
may operate on different databases or other backend data sources, for example.

Alternately, a single request may invoke multiple top-level operations which, again,
can execute in parallel.

It's very easy to convert a normal synchronous field resolver into an
asynchronous field resolver:
Instead of returning a normal value, an asynchronous field resolver
returns a special kind of :doc:`ResolverResult <resolve-as>`, a
``ResolverResultPromise``.

Such a promise is created by ``com.walmartlabs.lacinia/resolve-promise``.

The field resolver function returns immediately, but will typically perform some work in a background
thread.
When the resolved value is ready, the ``deliver!`` method can be invoked on the promise.

.. literalinclude:: ../_examples/async-example.edn
   :language: clojure

.. sidebar:: core.async?

   In this example we are using the ``clojure.core.async/thread`` macro to perform work
   in a background thread, since that may be familiar.
   Lacinia does not use the ``org.clojure/core.async`` library; we like to keep
   dependencies minimal and options open.  It's relatively easy
   to link a core.async channel to a ``ResolverResultPromise``.

The promise is created and returned from the
field resolver function.
In addition, as a side effect, a thread is started to perform some work.
When the work is complete, the ``deliver!`` method on the promise will inform
Lacinia, at which point Lacinia can start to execute selections on the resolved value
(in this example, the user data).

On normal queries, Lacinia will execute as much as it can in parallel.
This is controlled by how many of your field resolvers return a promise rather than
a direct result.

Despite the order of execution, Lacinia ensures that the order of keys in the response
matches the order in the query.

.. sidebar:: GraphQL Spec

   Read about `execution <http://facebook.github.io/graphql/#sec-Normal-and-Serial-Execution>`_.

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

Lacinia will catch exceptions thrown by a field resolver function, but can't do anything
about exceptions thrown inside some other thread.

In the example above, any thrown exception is converted to an
:doc:`error map <resolve-as>`.

.. warning::

   Not catching exceptions will lead to promises that are never delivered and that
   will cause Lacinia to block indefinitely.
