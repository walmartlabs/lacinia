Injecting Resolvers
===================

Schemas start as EDN files, which has the advantage that symbols do not have to be quoted
(useful when using the ``list`` and ``non-null`` qualifiers on types).
However, EDN is data, not code, which makes it nonsensical to define field resolvers directly in the schema file.

One option is to use ``assoc-in`` to attach resolvers after reading the EDN, but before invoking
:api:`schema/compile`.
This can become quite cumbersome in practice.

Instead, the standard approach is use
:api:`util/inject-resolvers`, [#attach-resolvers]_ which is a concise way of matching fields to resolvers, adding
the ``:resolve`` key to the nested field definitions.

.. literalinclude:: /_examples/compile-schema.clj
   :language: clojure

The keys in the map passed to ``inject-resolvers`` use the namespace to define the object (such as ``Human`` or ``Query``) and the local name to define the field within the object (``hero`` and so forth).

The ``inject-resolvers`` step occurs **before** the schema is compiled.

.. [#attach-resolvers] An older approach is still supported, via the function
   :api:`util/attach-resolvers`, but ``inject-resolvers`` is preferred, as it is simpler.
