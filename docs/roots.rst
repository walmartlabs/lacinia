Root Object Names
=================

Top-level query, mutation, and subscription operations are represented in Lacinia as fields on
special objects.
These objects are the "operation root objects".
The default names of these objects are ``QueryRoot``, ``MutationRoot``, and ``SubscriptionRoot``.

When compiling an input schema, these objects will be created if they do not already exist.

The ``:roots`` key of the input schema is used to override the default names. Inside ``:roots``, you
may specify keys ``:query``, ``:mutation``, or ``:subscription``, with the value being the name of the
corresponding object.

If the objects already exist, then
any fields on the objects are automatically available operations.
In the larger GraphQL world (beyond Lacinia), this is the typical way of defining operations.

Beyond that, the operations from the ``:queries``, etc. map of the input schema will be
merged into the fields of the corresponding root object.

Name collisions are not allowed; a schema compile exception is thrown if an operation (via ``:queries``, etc.)
conflicts with a field of the corresponding root object.

Unions
------

It is allowed for the root type to be a union.
This can be a handy way to organize many different operations.

In this case, Lacinia creates a new object
and merges the fields of the members of the union, along with any operations from the input schema.

Again, the field names must not conflict.

The new object becomes the root operation object.

Internal Names
--------------

In some cases, you may see error messages that refer to fields in the
``:__Queries``, ``:__Mutations``, or ``:__Subscriptions``
objects.
Internally, Lacinia constructs these objects, building ``:__Queries`` from the ``:queries`` map
(and so forth).
The fields may then be merged from these internal objects into the actual operation root objects.
