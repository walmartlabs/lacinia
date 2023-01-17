Using ResolverResult
====================

In the simplest case, a field resolver will do its job, resolving a value, simply by returning the value.

However, there are several other scenarios:

* There may be errors associated with the field which must be communicated back to Lacinia (for the
  top-level ``:errors`` key in the result map)

* A field resolver may want to introduce :doc:`changes into the application context <context>`
  as a way of communicating to deeply nested field resolvers

* A field resolver may operate :doc:`asynchronously <async>`, and want to return a promise for data that will
  be available in the future

.. sidebar:: GraphQL Spec

   Read about :spec:`errors <Errors>`.

Field resolvers should not throw exceptions; instead, if there is a problem generating the resolved value,
they should use the :api:`resolve/resolve-as` function to return a ResolverResult value.

When using ``resolve-as``, you may pass the error map as the second parameter (which is optional).
This first parameter is the resolved value, which may be ``nil``.

.. sidebar:: Why not just throw an exception?

    Exceptions are a terrible way to deal with control flow issues, even in the
    presence of actual failures.
    More importantly, the ResolverResult approach works well with Lacinia's
    :doc:`asynchronous processing features <async>`.

Errors will be exposed as the top-level ``:errors`` key of the execution result.

Error maps must contain at a minimum a ``:message`` key with a value of type String.

You may specify other keys and values as you wish, but these values will be part of the ultimate
result map, so they should be both concise and safe for the transport medium.
Generally, this means not to include values that can't be converted into JSON values.

In the result map, error maps are transformed; they contain the ``:message`` key, as well
as ``:locations``, ``:path``, and (sometimes) ``:extensions``.

.. literalinclude:: /_examples/errors-result.edn
   :language: clojure

The ``:locations`` key identifies where in the query document, as a line and column address,
the error occured.
It's value is an array (normally, a single value) of location maps; each location map
has ``:line`` and ``:column`` keys.

The ``:path`` associates the error with a location in the result data; this is seq of the names of fields
(or aliases for fields).
Some elements of the path may be numeric indices into sequences, for fields of type list.
These indices are zero based.

Any additional keys in the error map are collected into the ``:extensions`` key (which is only present
when the error map has such keys).

The order in which errors appear in the ``:errors`` key of the result map is not specified;
however, Lacinia does remove duplicate errors.

Tagging Resolvers
-----------------

If you write a function that *always* returns a ResolverResult, you should set the tag of the
function to be :api:`resolve/ResolverResult`.
Doing so enables an optimization inside Lacinia - it can skip the code that checks to see if
the function did in fact return a ResolverResult and wrap it in a ResolverResult if not.

Unfortunately, because of how Clojure handles function meta-data, you need to write your
function as follows:

.. literalinclude:: ../_examples/tagged-resolver.edn
   :language: clojure

This places the type tag on the function, not on the symbol (as normally happens with ``defn``).

It doesn't matter whether the function invokes ``resolve-as`` or ``resolve-promise``, but returning
nil or a bare value from a field resolver tagged with ResolverResult will cause runtime exceptions, so be careful.

Default Resolvers
-----------------

When a field does not have an explicit resolver in the schema, a default resolver is provided by Lacinia
(this is just one of the many operations that occur when compiling a schema);
ultimately, every field has a resolver, but the vast majority are these default resolvers.

The default resolver  maps the field name directly to a map key; a field named ``userName`` will default to a keyword key, ``:userName``; it does no
conversions beyond that, so there is no magic mapping from ``userName`` to ``:user-name``, for example.

Nested Resolver Results
-----------------------

Normally, a field resolver for a non-scalar field returns a map; the map is recusively selected by any nested fields
(a field with a list type will return a seq of raw values).

It is allowed that nested values are themselves ResolverResult instances; this is an alternative to defining resolvers
for the nested fields, and only really makes sense for a ResolverResultPromise (an asynchronous result).

This has an advantages: there's no need to define additional resolvers, and in some cases, no need to pass extra state
in the context needed by the nested resolvers.

The disadvantage is that the field in question may not be selected in the query and the asynchronous work being performed
will simply be discarded.

