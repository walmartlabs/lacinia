Fields
======

Fields are the basic building block of GraphQL data.

:doc:`objects` and :doc:`interfaces <interfaces>` are composed of fields.
Queries and mutations are a special kind of field.

**Fields are functions**. Or, more specifically, fields are a kind of operation
that begins with some data, adds in other details (such as field arguments provided
in the query), and produces new data that can be incorporated into a response.

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

.. warning::

   |lib| does not fully implement the spec here, which allows for more combinations of
   non-null and list than is currently supported.

The built-in scalar types:

.. sidebar:: GraphQL Spec

   Read about `scalar types <https://facebook.github.io/graphql/#sec-Built-in-Scalars>`_.

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
^^^^^^^^^^^^^^

The ``:resolve`` key identifies the field resolver function, used to provide the actual data.

This data, the *resolved value*, is never directly returned to the client; this is because
in GraphQL, the client query identifies which fields from the resolved value are selected
(and often, renamed) to form the response value.

``:resolve`` is optional; when not provided it is assumed that the containing field's
resolved value is a map containing a key matching the field's name.

.. tip::

   Clojure conventions are to name things as ``names-with-dashes`` rather than ``CamelCase``.
   However, it is perfectly valid to mix and match, to fit the expectations of your
   clients; nothing prevents a keyword from having mixed case, e.g., ``:Product``.

   GraphQL forbids any names from having dashes in them.

   |lib| is opinionated here: it converts field names with underscores into
   key names with dashes.
   For example, a field named ``:user_id`` would normally be resolved using the key ``:user-id``.

A resolve keyword or function may be provided on a field inside an
:doc:`object definition <objects>`, or
inside a :doc:`query definition <queries>` or
:doc:`mutation definition <mutations>`.  No resolve should be provided
in the fields of an :doc:`interface definition <interfaces>`: if provided in an interface
definition, the resolve will be silently ignored.

More commonly, ``:resolve`` can be a function.

.. sidebar:: Field Resolvers

   Please refer to the :doc:`full description of field resolvers <resolve/index>`.

The field's resolver is passed the resolved value of the **containing** field, object, query, or mutation.

The return value may be a scalar type, or a structured type, as defined by the
field's ``:type``.

For composite (non-scalar) types, the client query **must** include a nested set of fields
to be returned in the response.
The query is a tree, and the leaves of that tree will always be simple scalar values.

Arguments
^^^^^^^^^

A field may define arguments using the ``:args`` key; this is a map from argument name to
an argument definition.

A field uses arguments to modify what data, and in what order, is to be returned.
For example, arguments could set boundaries on a query based on date or price, or determine
sort order.

Argument definitions define a value for ``:type``, and may optionally provide a ``:description``.
Arguments do **not** have resolvers, as they represent explicit data from the client
passed to the field.


Description
^^^^^^^^^^^

A field may include a ``:description`` key; the value is a string exposed through :doc:`introspection`.
