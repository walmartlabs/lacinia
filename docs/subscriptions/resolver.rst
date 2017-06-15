Resolver
========

Unlike a query or mutation, the :doc:`field resolver <../resolve/index>`
for a subscription always starts with a specific value, provided by the streamer.

Because of this, the resolver is optional: if not provided, a default resolver is used, one that simply returns
the streamer value (the value provided to the event handler).

However, it still makes sense to implement a resolver in some cases.

Both the resolver and the streamer receive the same map of arguments: it is reasonble that some
may be used by the streamer (for example, to filter which events go into the source stream),
and some by the resolver (to control the selections on the source value).

