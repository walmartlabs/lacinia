Overview
========

The specification discusses a `source stream` and a `response stream`.

Lacinia implements the source stream as a callback function.
The response stream is largely the responsibility of
the :doc:`web tier <lacinia-pedestal>`.

- Lacinia invokes a streamer function once, to initialize the subscription stream.

- The streamer is provided with a source stream callback function; as new values are available
  they are passed to this callback.

  Typically, the streamer will create a thread, core.async process, or other long-lived
  construct to feed values to the source stream.

- Whenever the source stream callback is passed a value,
  Lacinia will execute the the subscription as a query, which will generate a
  new response (with the standard ``:data`` and/or ``:errors`` keys).

- The response will be converted as necessary and streamed to the client, forming
  the response stream.

- The streamer must return a function that will be invoked to perform cleanup.
  This cleanup function typically stops whatever process was started earlier.

Subscriptions are operations, like queries or mutations.
They are defined using the top-level ``:subscriptions`` key in the schema.

