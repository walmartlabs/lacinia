Resolver
========

Unlike a query or mutation, the :doc:`field resolver <../resolve/index>`
for a subscription always starts with a specific value, provided by the streamer, via
the source stream callback.

Because of this, the resolver is optional: if not provided, a default resolver is used, one that simply returns
the source stream value.

However, it still makes sense to implement a resolver in some cases.

Both the resolver and the streamer receive the same map of arguments: it is reasonable that some
may be used by the streamer (for example, to filter which values go into the source stream),
and some by the resolver (to control the selections on the source value).

