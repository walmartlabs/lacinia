Exceptions
==========

Field resolvers are responsible for catching any exceptions that occur.

Uncaught exceptions will bubble up out of Lacinia code entirely.

This is not desirable: better to return a partial response along with errors.

Field resolvers should catch exceptions and use :doc:`ResolverResults <resolve-as>`
to communicate errors back to Lacinia for inclusion in the ``:errors`` key of the result.

Failure to catch exceptions is even more damaging when using :doc:`async field resolvers <async>`.
