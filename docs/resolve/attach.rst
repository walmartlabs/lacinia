Attaching Resolvers
===================

Schemas start as EDN files, which has the advantage that symbols do not have to be quoted
(useful when using the `list` and `non-null` qualifiers on types).
However, EDN is data, not code, which makes it a problem to attach resolvers to the schema.

One option is to use ``assoc-in`` to attach resolvers after reading the EDN, but before invoking
``com.walmartlabs.lacinia.schema/compile``.

Instead, the standard approach is to put keyword placeholders in the EDN file, and then use
``com.walmartlabs.lacinia.util/attach-resolvers``, which walks the tree and makes the changes, replacing
the keywords with the actual functions.

.. literalinclude:: ../../dev-resources/org/example/schema.clj
   :language: clojure

This step occurs **before** the schema is compiled.


Resolver Factories
------------------

There are often cases where many fields will need very similar field resolvers.
A second resolver option exist for this case, where the schema references a *field resolver factory*
rather than a field resolver itself.

In the schema, the value for the ``:resolve`` key is a vector of a keyword and then
additional arguments:

.. literalinclude:: /_examples/resolver-factory-schema.edn
   :language: clojure

In the code, you must provide the field resolver factory:

.. literalinclude:: /_examples/resolver-factory.edn
   :language: clojure

The ``attach-resolvers`` function will see the ``[:literal  "Hello World"]`` in the
schema, and will invoke ``literal-factory``, passing it the ``"Hello World"`` argument.
``literal-factory`` is responsible for returning the actual field resolver.

A field resolver factory may have any number of arguments.

Common uses for field resolver factories:

- Mapping GraphQL field names to Clojure hyphenated names
- Converting or formatting a raw value into a selected value
- Accessing a deeply nested value in a structure

