Overview
========

Each operation (query, mutation, or subscription) will have a root field resolver.
Every field inside the operation or other object will have
a field resolver: if an explicit one is not provided, Lacinia creates
a default one.

A field resolver's responsibility is to *resolve* a value; in the simplest case,
this is accomplished by just returning the value.

As you might guess, the processing of queries into result map data is quite recursive.
The initial operation's field resolver is passed nil as the container resolved value.

The root field resolver will return a map [#root-value]_ ; as directed by the client's query, the fields
of this object will be selected and the top-level object passed to to the field resolvers
for the fields in the selection.

This continues down, where at each layer of nested fields in the query,
the containing field's resolved value is passed
to each field's resolver function, along with the global context, and the arguments
specific to that field.

The rules of field resolvers:

- A operation will resolve to a map of keys and values (or resolve to a sequence of such maps).
  The fields requested in the client's query will be used to resolve nested selections.

- Each field is passed its containing field's resolved value.
  It then returns a resolved value, which itself may be passed to its sub-fields.

.. tip::

   It is possible to :doc:`preview nested selections <selections>` in a field resolver, which can be used
   to implement some important optimizations.

Meanwhile, the selected data from the resolved value is added to the result map.

If the value is a scalar type, it is added as-is.

Otherwise, the value is a structured type, and the query **must** provide nested selections.

Field Resolver Arguments
------------------------

A field resolver is passed three arguments:

* The application context.

* The field's arguments.

* The containing field's resolved value.


Application Context
```````````````````

The application context is a map passed to the field resolver.
It allows some global state to be passed down into field resolvers; the
context is initially provided to ``com.walmartlabs.lacinia/execute``.
The initial application context may even be nil.

Many resolvers can simply ignore the context.

.. warning::

   Lacinia will frequently add its own keys to the context; these will be namespaced keywords.
   Please do not attempt to make use of these keys unless they are explicitly documented.
   Undocumented keys are not part of the Lacinia API and are
   subject to change without notice.

Field Arguments
```````````````

This is a map of arguments provided in the query.
The arguments map has keyword keys; the value types are as determined by
definition of the field argument.

If the argument value is expressed as a query variable, the variable will be resolved to
a simple value when the field resolver is invoked.

Container's Resolved Value
``````````````````````````

As mentioned above, the execution of a query is highly recursive.
The operation, as specified in the query document, executes first; its resolver is passed
nil for the container resolved value.

The operation's resolved value is passed to the field resolver for each field nested in the
operation.

For scalar types, the field resolver can simply return the selected value.

For structured types, the field resolver returns a resolved value;
the query *must* contain nested selections.
These selections will trigger further fields, whose resolvers will be passed the resolved value.

For example, you might have a ``:lineItem`` query of type ``:LineItem``, and LineItem might include a field,
``:product`` of type ``:Product``.
A query ``{lineItem(id:"12345") { product }}`` is not valid: it is not possible to return a Product directly,
you **must** select specific fields within Product:  ``{lineItem(id:"12345") { product { name upc price }}}``.

.. tip::

   Generally, we expect the individual values to be Clojure maps (or records).
   Lacinia supports :doc:`other types <type-tags>`, though that creates a bit of a burden
   on the developer to provide the necessary resolvers.

Resolving Collections
---------------------

When an operation or field resolves as a collection, things are only slightly different.

The nested selections are applied to **each** resolved value in the collection.

Default Field Resolver
----------------------

In the majority of cases, there is a direct mapping from a field name (in the schema) to a key
of the resolved value.

When the ``:resolve`` key for a field is not specified, a default resolver
is provided automatically; this default resolver simply expects the container resolved value to be a map
containing a key that exactly matches the field name.

It is even possible to customize this default field resolver, as an option passed to
``com.walmartlabs.lacina.schema/compile``.

.. [#root-value] Or, in practice, a sequence of maps.
   In theory, an operation type could be a scalar, but use cases for this are rare.
