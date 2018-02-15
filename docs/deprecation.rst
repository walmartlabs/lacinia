Deprecation
===========

Schemas grow and change over time.
GraphQL values backwards compatibility quite highly, so changes to a schema are typically additive:
introducing novel fields, types, and enum values.

However, when the implementation of a field is just `wrong`, it can be kept for compatibility, but
deprecated.

Both :doc:`fields <fields>` and :doc:`enums <enums>` can include a ``:deprecated`` key.
Remember that queries, mutations, and subscriptions are (under the covers) just fields, so they can be
deprecated as well.

The value for the key can either be ``true``, or a string description of the reason the field is deprecated.
Typically, the description indicates an alternative field to use instead.

Deprecation does *not* affect execution of the field in any way; the deprecation flag and reason simply shows up
in :doc:`introspection <introspection>`.


