Enums
=====

.. sidebar:: GraphQL Spec

   Read about :spec:`enums <Enums>`.

GraphQL supports enumerated types, types whose value is limited to a explicit list.

.. literalinclude:: _examples/enum-definition.edn
   :language: clojure

It is allowed to define enum values as either strings, keywords, or symbols.
Internally, the enum values are converted to keywords.

Enum values must be unique, otherwise an exception is thrown when compiling the schema.

Enum values must be GraphQL Names: they may contain only letters, numbers, and underscores.

Enums `are` case sensitive; by convention they are in all upper-case.

When an enum type is used as an argument, the value provided to the field resolver function
will be a keyword, regardless of whether the enum values were defined using strings, keywords, or symbols.

Field resolvers are required to return a keyword, and that keyword must match one of the values in the enum.

As with many other elements in GraphQL, a description may be provided for the enum (for use with
:doc:`introspection`).

To provide a description for individual enum values, a different form must be used:

.. literalinclude:: _examples/enum-definition-description.edn
   :language: clojure

The ``:description`` key is optional.

You may include the ``:deprecated`` key used to mark a single value as :doc:`deprecated <deprecation>`.

You may mix-and-match the two forms.

Parse and Serialize
-------------------

Normally, when using enums, you must match your application's data model to the GraphQL model; for enums
that means that you will receive (via field arguments) enum values as simple keywords.
Your resolver code must provide enums as strings, keywords, or symbols that match one of the defined values
for the enum.

However, in Clojure we often use namespaced keywords in our application model, or other representations
of enum values specific to your application.
Starting in Lacinia 0.36.0, it is possible to control the mapping between the GraphQL model (the
simple keywords) and your application model.

Much like :doc:`scalars <custom-scalars>` you may `optionally`
provide a ``:parse`` and ``:serialize`` for enums, but the
intention is slightly different.

For an enum, the ``:parse`` function is passed a valid enum keyword and returns a value used by the application's
data model ... most often, this is a namespaced keyword.

The default ``:parse`` function is identity; the GraphQL value is the same as the application value,
an unqualified keyword.

The ``:serialize`` function is the opposite; it converts from the application model back to
a GraphQL value, which is then verified to be valid for the enum.

The function :api:`util/inject-enum-transformers` is an easy way to add the ``:parse`` and ``:serialize`` functions
to the schema.




