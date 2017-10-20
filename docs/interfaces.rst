Interfaces
==========

GraphQL supports the notion of *interfaces*, collections of fields and their arguments.

.. sidebar:: GraphQL Spec

   Read about :spec:`interfaces <Interfaces>`.


To keep things simple, interfaces can not extend other interfaces.
Likewise, objects can implement multiple interfaces, but can not extend other objects.

Interfaces are valid types, they can be specified as the return type of a query, mutation,
or as the type of a field.

.. literalinclude:: _examples/interface-definition.edn
   :language: clojure


An interface definition may include a ``:description`` key; the value is a string exposed through :doc:`introspection`.

The description on an interface field, or on an argument of an interface field, will be inherited by
the object field (or argument) unless overriden.
This helps to elimiate duplication of documentation between an interface and the object implementing
the interface.

The :doc:`object definition <objects>` must include all the fields of all extended interfaces.


.. tip::

   When a field or operation type is an interface,
   the field resolver may return any of a number of different
   concrete object types, and Lacinia has no way to determine which;
   this information must be :doc:`explicitly provided <resolve/type-tags>`.
