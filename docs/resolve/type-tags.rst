Explicit Types
==============

For structured types, Lacinia needs to know what type of data is returned by the field resolver,
so that it can, as necessary, process query fragments.

When the type of field is a concrete object type, Lacinia automatically tags the value with
the schema type.

When the type of a field is an interface or union, it is necessary for the field resolver
to explicitly tag the value with its object type.

Using tag-with-type
-------------------

The function :api:`schema/tag-with-type` exists for this purpose.
The tag value is a keyword matching an object definition.

When a field returns a list of an interface, or a list of a union,
then each individual resolved value must be tagged with its concrete type.
It is allowed and expected that different values in the collection will have
different concrete types.

Generally, type tagging is just metadata added to a map (or Clojure record type).
However, Lacinia supports tagging of arbitrary objects that don't support Clojure metadata
... but ``tag-with-type`` will return a wrapper type in that case.  When using Java types,
make sure that ``tag-with-type`` is the last thing a field resolver does.


Using record types
------------------

As an alternative to ``type-with-tag``, it is possible to associate an object with a Java class; typically
this is a record type created using ``defrecord``.

The ``:tag`` key of the object definition must be set to the the class name (as a symbol).

.. literalinclude:: /_examples/object-tag.edn
   :language: clojure
   :emphasize-lines: 6,14

This only works if the field resolver functions return the corresponding record types, rather than
ordinary Clojure maps.
In the above example, the field resolvers would need to invoke the ``map->Business`` or ``map->Employee`` constructor
functions as appropriate.

.. tip::

   The ``:tag`` value is a Java class name, not a namespaced Clojure name.
   That means no slash character, and dashes in the namespace must be converted to underscores.

Container type
--------------

When a field resolver is invoked, the context value for key ``:com.walmartlabs.lacinia/container-type-name``
will be the name of the concrete type (a keyword) for the resolved value passed into the resolver.
This will be nil for top-level operations.

When the type of the containing field is a union or interface, this value will be the specific concrete object type
for the actual resolved value.
