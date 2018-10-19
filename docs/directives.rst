Directives
==========

Directives provide a way to describe additional options to the GraphQL executor.

Directives is a GraphQL term; in practice, directives are much like meta data in Clojure,
or annotations in Java.

.. sidebar:: GraphQL Spec

   Read about directives :spec:`here <Language.Directives>`
   and :spec:`here <Type-System.Directives>`.

Directives allow Lacinia to change the incoming query based on additional criteria.
For example, we can use directives to include or skip a field if certain criteria are met.

Currently Lacinia supports just the two standard query directives: ``@skip`` and ``@include``, but future versions
may include more.

.. warning::

  Directive support is currently in transition towards some support for custom directives.

Directives in Schema IDL
------------------------

When using :doc:`schema/parsing`, the `directive` keyword allows new directives to be defined.
Directive definitions can be defined for executable elements (such as a field selection in a query document), or
for type system elements (such as an object or field definition in the schema).

Directives may also be defined in an EDN schema; the root ``:directive-defs`` element is a map of directives types
to directive definition.  A directive defintion defines the types of any arguments, as well as a set of locations.

.. literalinclude:: _examples/directive-defs.edn
   :language: clojure

This defines a field definition directive, ``@access``, and applies it to the  ``ultimate_answer``
query field.

Directive Validation
--------------------

Directives are validated:

* Directives may have arguments, and the argument types must be rooted in known :doc:`scalar <fields>` types.

* Directives may be placed on schema elements (objects and input objects, unions, enums and enum values, fields, etc.).
  Directives placed on an element are verified to be applicable to the location.

.. warning::

   Directive support is evolving quickly; full support for directives, including argument type validation,
   is forthcoming, as is an API to identify schema and executable directives.

   The goal of the current stage is to support parsing of SDL schemas that include directive definitions
   and directives on elements.

   :doc:`introspection` hasn't caught up to to these changes; custom directives are not identified, nor
   are directives on elements.

@deprecated directive
---------------------

The ``@deprecated`` directive is supported.
This enables :doc:`deprecation` of object fields and enum values.


