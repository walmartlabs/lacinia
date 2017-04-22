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

