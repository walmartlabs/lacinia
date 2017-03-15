Interfaces
==========

GraphQL supports the notion of *interfaces*, collections of fields and their arguments.

.. sidebar:: GraphQL Spec

   Read about `interfaces <https://facebook.github.io/graphql/#sec-Interfaces>`_.


To keep things simple, interfaces can not extend other interfaces.
Likewise, objects can implement multiple interfaces, but can not extend other objects.

Interfaces are valid types, they can be specified as the return type of a query, mutation,
or as the type of a field.

.. literalinclude:: _examples/interface-definition.edn
   :language: clojure


An interface definition may include a ``:description`` key; the value is a string exposed through :doc:`introspection`.

The :doc:`object definition <objects>` must include all the fields of all extended interfaces.

