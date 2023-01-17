Injecting Resolvers
===================

Schemas start as EDN files, which has the advantage that symbols do not have to be quoted
(useful when using the ``list`` and ``non-null`` qualifiers on types).
However, EDN is data, not code, which makes it nonsensical to defined field resolvers directly in the schema.

One option is to use ``assoc-in`` to attach resolvers after reading the EDN, but before invoking
:api:`schema/compile`.
This can become quite cumbersome in practice.

Instead, the standard approach is to put keyword placeholders in the EDN file, and then use
:api:`util/inject-resolvers`, [#attach-resolvers]_ which walks the schema tree and makes the changes, replacing
the keywords with the actual functions.

.. literalinclude:: /_examples/compile-schema.clj
   :language: clojure

The ``inject-resolvers`` step occurs **before** the schema is compiled.

.. [#attach-resolvers] An older approach is still supported, via the function
   :api:`util/attach-resolvers`, but ``inject-resolvers`` is preferred.
