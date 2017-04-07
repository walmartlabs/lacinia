Using ResolverResult
====================

A field resolver usually just returns the resolved value, or (for a list type) a seq of resolved values.

.. sidebar:: GraphQL Spec

   Read about `errors <http://facebook.github.io/graphql/#sec-Errors>`_.

What if you want to add errors?  This is accomplished with a special return value, ResolverResult, which
contains both a resolved value and an error map, or seq of error maps.

Field resolvers should not throw exceptions; instead, if there is a problem generating the resolved value,
they should use the ``com.walmartlabs.lacinia.resolve/resolve-as`` function to return a ResolverResult value.

.. sidebar:: Why not just throw an exception?

    Exceptions are a terrible way to deal with control flow issues, even in the
    presence of actual failures.
    More importantly, the ResolverResult approach allows more than a single error, and
    works well with Lacinia's
    :doc:`asynchronous processing features <async>`.

Errors will be exposed as the top-level ``:errors`` key of the execution result.

Error maps contain at a minimum a ``:message`` key of type String.
You may specify other keys and values as you wish, but these values will be part of the ultimate
client response, so they should be both concise and safe for the transport medium.
Generally, this means not to include values that can't be converted into JSON values.

Lacinia will add additional keys to the error map, to identify the query selection active when the
field resolver was invoked.
This includes location data related to the input query document.

When using ``resolve-as``, you may pass the error map as the second parameter (which is optional).
You may pass a single map, or a seq of error maps.
This first parameter is the resolved value, which may be ``nil``.

The order in which errors appear in the ``:errors`` key of the response is not specified;
however, Lacinia does remove duplicate errors.
