Exceptions
==========

Field resolvers are responsible for catching any exceptions that occur.

Uncaught exceptions are *not* converted to errors; they are caught, wrapped in a new
exception to identify the field name, field arguments, query path, and query location, but
then allowed to bubble up out of Lacinia entirely.

This is not desirable: better to return a partial result along with errors.

Field resolvers should catch exceptions and use :doc:`ResolverResults <resolve-as>`
to communicate errors back to Lacinia for inclusion in the ``:errors`` key of the result.

Failure to catch exceptions is even more damaging when using :doc:`async field resolvers <async>`,
as this can cause query execution to entirely halt, due to resolver result promises never being
delivered.
