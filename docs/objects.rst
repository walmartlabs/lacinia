Objects
=======

A schema object defines a single type of data that may be queried.
This is often a mapping from data obtained from a database or other external store
and exposed by GraphQL.

.. sidebar:: GraphQL Spec

   Read about `server-side types <https://facebook.github.io/graphql/#sec-Type-System>`_.

GraphQL supports objects, interfaces, unions, and enums.

A simple object defines a set of fields, each with a specific type.
Object definitions are under the ``:objects`` key of the schema.

.. literalinclude:: _examples/object-definition.edn
   :language: clojure


This defines a schema containing only a single schema object [#emptyschema]_, `product`, with four fields:

* id - an identifier
* name - a string
* sku - a string
* keyword - a list of strings

Field Definitions
-----------------

An object definition contains a ``:fields`` key, whose value is a map from field name to
:doc:`field definition <fields>`. Field names are keywords.

Interface Implementations
-------------------------

An object may implement zero or more :doc:`interfaces <interfaces>`.
This is described using the ``:implements`` key, whose value is a list of keywords identifying interfaces.

Objects can only implement interfaces: there's no concept of inheritance from other objects.

An object definition must include all the fields from all implemented interfaces; failure to do so
will cause an exception to be thrown when the schema is compiled.

In some cases, a field defined in an object may be more specific than a field from an inherited
interface; for example, the field type in the interface may itself be an interface; the field
type in the object must be that exact interface *or* an object that implements that interface.

In our Star Wars themed example schema, we see that the ``:character`` interface defines the ``:friends`` field
as type ``(list :character)``. So, in the generic case, the friends of a character can be either Humans or Droids.

Perhaps in a darker version of the Star Wars universe, Humans can not be friends with Droids.
In that case, the ``:friends`` field of the ``:human`` object would be type
``(list :human)`` rather than the more egalitarian ``(list :character)``.
This appears to be a type conflict, as the type of the ``:friends`` field differs between ``:human`` and ``:character``

In fact, this does not violate type constraints, because a Human is always a Character.

Object Description
------------------

An object definition may include a ``:description`` key; the value is a string exposed through :doc:`introspection`.

When an object implement an interface, it may omit the ``:description`` of inherited fields, and on
arguments of inherited fields to inherit the description from the interface.

.. [#emptyschema] A schema that fails to define either queries or mutations is useful only as an example.
