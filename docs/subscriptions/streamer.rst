Streamer
========

The streamer is responsible for initiating and managing the
the source stream.

The streamer is provided as the ``:stream`` key in the subscription definition.

.. literalinclude:: ../_examples/subs-schema.edn
  :language: clojure

Streamers parallel :doc:`field resolvers <../resolve/index>`, and a function,
:api:`util/attach-streamers`, is provided to replace
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

Regardless, the streamer provides the stream of source values, by making successive calls to
the provided source stream callback function, and it provides a way to cleanup the subscription, by
returning a cleanup function.

The subscription stays active until either the client closes the connection, or
until ``nil`` is passed to the source stream callback.

In either case, the cleanup callback will then be invoked.

Invoking the streamer
---------------------

The streamer must be invoked with the parsed query and source stream callback in order to setup the
subscription using :api:`executor/invoke-streamer`.

.. literalinclude:: ../_examples/invoke-streamer.edn
  :language: clojure

Typically subscriptions are used with websockets so this example could be adapted to receive a message
with a query and variables from a connected websocket client. Then any messages received by the source
stream callback can be pushed to the client.

Timing
------

The source stream callback will return immediately.
It must return nil.

The provided value will be used to generate a GraphQL result map, which will be streamed to the client.
Typically, the result map will be generated asynchronously, on another thread.

Implementations of the source stream callback may set different guarantees on when or if values in the source stream
are converted to responses in the response stream.

Likewise, when the subscription is closed (by either the client or by the streamer itself),
the callback will be invoked asynchronously.

Notes
-----

The value passed to the source stream callback is normally a plain, non-nil value.

It may be a wrapped value (e.g., via :api:`resolve/with-error`).
This will be handled inside :api:`execute/execute-query` (which is invoked with the value passed to the callback).

For historical reasons, it may also be a ResolverResult; it is the implementation's job to obtain
the resolved value from this result before calling ``execute-query``; this is handled by lacinia-pedestal, for example.


