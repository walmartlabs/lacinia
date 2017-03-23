Overview
========

Each query or mutation will have a root field resolver.
Every field inside the query, mutation, or other object will have
a field resolver: if an explicit one is not provided, Lacinia creates
a default one.

As you might guess, the processing of queries into responses is quite recursive.
The initial query (or mutation) will invoke a top-level field resolver.
Here, the resolved value passed to the root field resolver will be nil.

The root field resolver will return a map [#root-value]_ ; as directed by the client's query, the fields
of this object will be selected and the top-level object passed to to the field resolvers
for the fields in the selection.

This continues down, where at each layer of nested fields in the query,
the containing field's resolved value is passed
to each field's resolver function, along with the global context, and the arguments
specific to that field.

The rules of field resolvers:

A query or mutation will resolve to a map of keys and values (or
resolve to a sequence of maps).
The fields requested in the client's query will be used to resolve nested values.

Each field is passed its containing field's resolved value.
It then returns a resolved value, which itself may be passed to its sub-fields.

Meanwhile, the selected data from the resolved value is added to the response.

If the value is a scalar type, it is added as-is.

Otherwise, the value is a structured type, and the query must provide sub-fields.

Field Resolver Arguments
------------------------

A field resolver is passed three parameters:

* The application context.

* The field's arguments.

* The containing field's resolved value.


Application Context
```````````````````

The application context is a map passed to the field resolver.
It allows some global state to be passed down into field resolvers; the
context is initially provided to ``com.walmartlabs.lacinia/execute``.
The initial application context may even be nil.

Before invoking a field resolver, the context is extended with an additional key:

``:com.walmartlabs.lacinia/selection``
    The field selection from the parsed GraphQL query.

The field selection can be used to optimize what data is needed from an external store; in SQL terms,
knowing what data the client needs means that unnecessary table joins can be avoided, or the
queries otherwise optimized.

.. warning::

    An example of this is forthcoming.

    A forthcoming API will allow the field selection to be queried for relevant data.

Many resolvers can ignore the context.

Field Arguments
```````````````

This is a map of arguments provided in the query.

Container's Resolved Value
``````````````````````````

As mentioned above, the execution of a query is highly recursive.
The operation, as specified in the query document, executes first; its resolver is passed
nil for the container resolved value.

The operation's resolved value is passed to the field resolver for each field nested in the
operation.

For scalar types, the field resolver can simply return the selected value.

For structured types, the field resolver returns a resolved value;
the query *must* contain nested fields which will be passed the resolved value, to make selections
from the resolved value.

For example, you might have an ``:lineItem`` query of type ``:LineItem``, and LineItem might include a field,
``:product`` of type ``:Product``.
A query ``{lineItem(id:"12345") { product }}}`` is not valid: it is not possible to return a Product directly,
you **must** select fields within Product:  ``{lineItem(id:"12345") { product { name upc price }}}```.


Resolver Result
---------------

A field resolver usually just returns the resolved value, or (for a list type) a seq of resolved values.

Field resolvers should not throw exceptions; instead, if there is a problem generating the resolved value,
they should use the ``com.walmartlabs.lacinia.resolve/resolve-as`` function to return a ResolverResult value.

.. sidebar:: Why not just throw an exception?

    Exceptions are a terrible way to deal with control flow issues, even in the
    presence of actual failures.
    More importantly, the ResolverResult approach allows more than a single error, and keeps the
    door open for planned extensions that will support asynchronous query execution.

Errors will be exposed as the top-level ``:errors`` key of the execution result.

Resolving Collections
---------------------

When an operation or field resolves as a collection, things are only slightly different.

The nested fields are applied to **each** resolved value in the collection.

Default Field Resolver
----------------------

In the majority of cases, there is a direct mapping from a field name (in the schema) to a key
of the resolved value.

When the ``:resolve`` key for a field is not specified, a default resolver
is provided:

* A key is formed, by converting any underscores in the field name to dashes.

* A function is created, that simply extracts the key from the container resolved value,
  and returns a resolved value tuple.
 
For example, if the field's name is ``:updated_at``, then
the default resolver will extract the ``:updated-at`` key.

This works well in cases where the resolved value contains the expected key with the correct type.

Nested Fields
-------------

Typically, a query will select fields from the operation, and then select fields within those fields.
In each case, the container's resolved value is passed to the nested field so that it can provide the
nested resolved value.
This continues as deeply as the query specifies.

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

.. [#root-value] Or, in practice, a sequence of maps.
   In theory, an operation type could be a scalar, but use cases for this are rare.
