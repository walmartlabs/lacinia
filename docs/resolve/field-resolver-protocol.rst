FieldResolver Protocol
======================

In the majority of cases, a field resolver is simply a function that accepts the three parameters:
context, args, and value.

However, when structuring large systems using
`Component <https://github.com/stuartsierra/component>`_ (or any similar approach), this can be inconvienient, as it
does not make it possible to structure field resolvers as components.

The :api:`resolve/FieldResolver`` protocol addresses this: it defines a single method, ``resolve-value`.
This method is the analog of an ordinary field resolver function.

The support for this protocol is baked directly into
:api:`schema/compile`.

Just like field resolver functions, FieldResolver instances can resolve a value directly by
returning it, or can return a ResolverResult.
