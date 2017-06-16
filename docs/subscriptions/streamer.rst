Streamer
========

The streamer is responsible for initiating and managing the
the source stream.

The streamer is provided as the ``:stream`` key in the subscription definition.

.. literalinclude:: ../_examples/subs-schema.edn
  :language: clojure

Streamers parallel :doc:`field resolvers <../resolve/index>`, and a function,
``com.walmartlabs.lacinia.util/attach-streamers``, is provided to replace
keywords in the schema with actual functions.

A streamer is passed three values:

* The application context

* The field arguments

* The source stream callback

The first two are the same as a field resolver; the third is a function
that accepts a single value.

The streamer should perform whatever operations are necessary for it
to set up the stream of values; typically this is registering as a listener
for updates to some form of publish/subscribe system.

As new values are published, the streamer must pass those values to the source stream callback.

Further, the streamer must return a function to clean up the stream when the subscription
is terminated.

.. literalinclude:: ../_examples/subs-streamer.edn
  :language: clojure

A lot of this example is hypothetical; it presumes ``create-log-subscription`` will return an value
that can be used with ``on-publish`` and ``stop-log-subscription``.
A real implementation might use Clojure core.async, subscribe to a JMS queue, or an almost
unbounded number of other options.

Regardless, the streamer provides the stream of source values, but making successive calls to
the provided source stream callback function, and it provides a way to cleanup the subscription, by
returning a cleanup function.

The subscription stays active until either the client closes the connection, or
until ``nil`` is passed to the source stream callback.

In either case, the cleanup callback will then be invoked.

Timing
------

The source stream callback will return immediately.
It must return nil.

The provided value will be used to generate a GraphQL response, which will be streamed to the client.
Typically, the response will be generated asynchronously, on another thread.

Implementations of the source stream callback may set different guarantees on when or if values in the source stream
are converted to responses in the response stream.

Likewise, when the subscription is closed (by either the client or by the streamer itself),
the callback will be invoked asynchronously.


