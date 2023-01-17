Fields
======

Fields are the basic building block of GraphQL data.

:doc:`objects` and :doc:`interfaces <interfaces>` are composed of fields.
Queries, mutations, and subscriptions are also special kinds of fields.

**Fields are functions**. Or, more specifically, fields are a kind of operation
that begins with some data, adds in other details (such as field arguments provided
in the query), and produces new data that can be incorporated into the overall result.

Field Definition
----------------

A field definition occurs in the schema to describe the type and other details of a
field.
A field definition is a map with specific keys.


Field Type
----------

The main key in a field definition is ``:type``, which is required.
This is the type of value that may be returned by the field resolver, and
is specified in terms of the type DSL.


Types DSL
---------

Types are the essence of fields; they can represent scalar values (simple values,
such as string or numbers), composite objects with their own fields,
or lists of either scalars or objects.


In the schema, a type can be:

- A keyword corresponding to an object, interface, enum, or union
- A scalar type (built in, or schema defined)
- A non-nillable version of any of the above: ``(non-null X)``
- A list of any of the above: ``(list X)``

The built-in scalar types:

.. sidebar:: GraphQL Spec

   Read about :spec:`scalar types <Built-in-Scalars>`.

* String
* Float
* Int
* Boolean
* ID

.. sidebar:: Conventions

  By convention, the built-in scalar types are identified as symbols, such as ``{:type String}``.
  Other types (objects, interfaces, enums, and unions) are identified as keywords,
  ``{:type (list :character)}``.
  It actually makes no difference; internally everything is converted to keywords in the
  compiled schema.

Field Resolver
--------------

The ``:resolve`` key in the field definition identifies the field resolver function, used to provide the actual data.  The ``:resolve`` key, being a function, is usually
:doc:`provided at runtime <resolve/attach>`.

This data, the *resolved value*, is never directly returned to the client; this is because
in GraphQL, the client query identifies which fields from the resolved value are selected
(and often, renamed) to form the result value.

When a specific resolver is not provided for a field, Lacinia will provide a simple default:
it is assumed that the containing field's resolved value is a map containing a key exactly matching the field's name.

.. sidebar:: Field Resolvers

   Please refer to the :doc:`full description of field resolvers <resolve/index>`.

The field's resolver is passed the resolved value of the **containing** field, object, query, or mutation.

The return value may be a scalar type, or a structured type, as defined by the
field's ``:type``.

For composite (non-scalar) types, the client query **must** include a nested set of fields
to be returned in the result map.
The query is a tree, and the leaves of that tree must always be simple scalar values.

Arguments
---------

A field may define arguments using the ``:args`` key; this is a map from argument name to
an argument definition.

A field uses arguments to modify what data, and in what order, is to be returned.
For example, arguments could set boundaries on a query based on date or price, or determine
sort order.

Argument definitions define a value for ``:type``, and may optionally provide a ``:description``.
Arguments do **not** have resolvers, as they represent explicit data from the client
passed to the field.

Arguments may also have a ``:default-value``.
The default value is supplied to the field resolver when the request does not itself supply
a value for the argument.

An argument that is not specified in the query, and does not have a default value, will be omitted
from the argument map passed to the :doc:`field resolver <resolve/index>`.


Description
-----------

A field may include a ``:description`` key; the value is a string exposed through :doc:`introspection`.

Deprecation
-----------

A field may include a ``:deprecated`` key; this identifies that the field
is :doc:`deprecated <deprecation>`.
