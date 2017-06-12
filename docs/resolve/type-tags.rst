Explicit Types
--------------

For structured types, Lacinia needs to know what type of data is returned by the field resolver,
so that it can, as necessary, process query fragments.

When the type of field is a concrete object type, Lacinia automatically tags the value with
the schema type.

When the type of a field is an interface or union, it is necessary for the field resolver
to explicitly tag the value with its object type.
The function ``com.walmartlabs.lacinia.schema/tag-with-type`` exists for this purpose.
The tag value is a keyword matching an object definition.

When a field returns a list of an interface, or a list of a union,
then each individual resolved value must be tagged with its concrete type.
It is allowed and expected that different values in the collection will have
different concrete types.

When a field resolver is invoked, the context value for key ``:com.walmartlabs.lacinia/container-type-name``
will be the name of the concrete type (a keyword) for the resolved value.

Generally, type tagging is just metadata added to a map (or Clojure record type).
However, Lacinia supports tagging of arbitrary objects that don't support Clojure metadata
... but ``tag-with-type`` will return a wrapper type in that case.  When using Java types,
make sure that ``tag-with-type`` is the last thing a field resolver does.
