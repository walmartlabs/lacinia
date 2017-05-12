Decorating Resolvers
====================

It can be useful to apply a decorator to the field resolver functions in your schema.
The most typical use case is to allow a field resolver to return a convenient type, such as
a core.async channel, or `Manifold <https://github.com/ztellman/manifold>`_ deferred, and convert it to a
Lacinia :doc:`ResolverResult promise <async>`.

Other potential uses include client authentication and authorization, logging, metrics collection, or other
cross-cutting concerns.

The decorator function is provided as the ``:decorator`` key of the ``com.walmart.lacinia.schema/compile` options map.

The function is passed the object name, field name, and resolver provided in the schema.

The object name and field name are keywords.  The object name may be ``:QueryRoot`` for queries and ``:MutationRoot`` for mutations.

The decorator must return the same function, or a wrapped version of the function.

Here's a simplified example of converting from a core.async channel to a ResolverResult:

.. literalinclude:: ../_examples/decorator.edn
   :language: clojure

A full implementation may want to be more focused (perhaps driven by meta-data on the
function); this decorator will be applied to all
resolver functions in the schema, *including* those that are part of the
:doc:`Introspection Schema <../introspection>`.
