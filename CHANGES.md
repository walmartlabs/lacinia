## 0.25.0 -- 2 Mar 2018

Enum validation has changed: field resolvers may now return
a keyword, symbol, or string.
Internally, the value is converted to a keyword before being added
to the response map.

However, field resolvers that return an invalid value for an enum field
results in a thrown exception: previously, this was handled as
a field error.

It is now possible to mark fields (including operations) and enum values
as deprecated.

Compiled schemas now print and pretty-print as `#CompiledSchema<>`.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/13?closed=1)

## 0.24.0 -- 30 Jan 2018

Added the FieldResolver protocol that allows a Clojure record, such as a
[Component](https://github.com/stuartsierra/component), to act as a field resolver.

Field resolvers for enum types are now required to return a keyword, and that
keyword must match one of the values defined for the enum.
Previously, Lacinia failed to peform any checks in this case, which could result
in invalid data present in the result map.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/12?closed=1)

## 0.23.0 -- 5 Dec 2017

Added `com.walmartlabs.lacina.resolve/wrap-resolver-result` which makes it easier
to wrap an existing resolver function, but safely manipulate the resolved
value.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/11?closed=1)

## 0.22.1 -- 27 Oct 2017

Fixes a bug that prevented operation names from working when a query
defined only a single operation.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/10?closed=1)

## 0.22.0 -- 24 Oct 2017

Previously, the reserved words 'query', 'mutation', and 'subscription'
could not be used as the name of an operation, variable, field, etc.
The grammar and parser have been changed to allow this.

Lacinia has a new API for parsing a GraphQL schema as an alternative
to using Lacinia's EDN format.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/9?closed=1)

## 0.21.0 -- 12 Sep 2017

Simplified a clojure.spec to prevent a potential runtime error
`Could not locate clojure/test/check/generators__init.class or clojure/test/check/generators.clj on classpath`.
This would occur when the :default-field-resolver option was specified, and org.clojure/test.check was
not on the classpath (it is a side-effect of having `com.walmartlabs.lacina/compile`
be always instrumented).

A number of small optimizations have been made, shaving a few milliseconds off selections
on very large lists.

## 0.20.0 -- 1 Aug 2017

Object fields and field arguments may now inherit their description from corresponding
fields and field arguments of an implemented interface.

There is now a mechanism to allow a ResolverResult callback to be invoked
in a application-provided executor or thread pool.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/7?closed=1)

## 0.19.0 -- 11 Jul 2017

Lacinia now includes support for GraphQL subscriptions.

Lacinia no longer catches and reports exceptions inside field resolvers.

It is now possible for a field resolver to modify the application context
passed to all nested field resolvers (at any depth).

The :decorator option to `com.walmartlabs.lacinia.schema/compile` has been
removed. This is a feature, added in 0.17.0, that can be better implemented
in application code.

The callback provided to the
`com.walmartlabs.lacinia.resolve/on-deliver!` protocol method has
changed to only accept a single value.
Previously, the callback received a resolved value and an nilable error map.
This method is not intended for use in application code.

The `com.walmartlabs.lacinia.schema/compile` function is now *always* instrumented.
This means that non-conforming schemas will fail with a spec verification exception.

[Closed Issues](https://github.com/walmartlabs/lacinia/issues?q=is%3Aclosed+milestone%3A0.19.0)

## 0.18.0 -- 19 June 2017

`com.walmartlabs.lacinia.schema/tag-with-type` now uses metadata in the majority of cases
(as it did in 0.16.0), resorting to a wrapper type only when
the value is a Java object that doesn't support metadata.

Queries with repeated identical fields (same alias and arguments) will now be merged together
along with their subselections.

Fixed Selections API functions throwing a NullPointerException on Introspective meta fields.

Upgraded to `clojure-future-spec` 1.9.0-alpha17.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/5?closed=1)

## 0.17.0 -- 22 May 2017

Lacinia now better implements the specification, in terms of parsing
and serializing scalars.

The default mapping from field name to field resolver has simplified;
it is now a direct mapping, without converting underscores to dashes.
The old behavior is still available via an option to
`com.walmartlabs.lacinia.schema/compile`.

The function `com.walmartlabs.lacinia.schema/tag-with-type` has changed; it
now returns a special wrapper value (rather than the same value with
different metadata). This is to allow resolved values that do not
support metadata, such as Java objects.

The related function `com.walmartlabs.lacinia.schema/type-tag` has been removed.

Please update your applications carefully.

`compile` now has a new option, `:decorator`.
The decorator is a callback applied to all non-default field resolvers.
The primary use case is to adapt the return value of a field resolver,
for example, from a core.async channel to a Lacinia ResolverResult.

New function: `com.walmartlabs.lacinia.parser/operations`: extracts
from a parsed query the type (mutation or query) and the set of
operations.

Several new *experimental* functions were added to `com.walmartlabs.lacinia.executor` to
expose details about the selections tree; these functions can be invoked
from a field resolver to preview what fields will be selected below
the field.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/4)

## 0.16.0 -- 3 May 2017

The function `com.walmartlabs.lacinia.schema/as-conformer` is now public.

New function `com.walmartlabs.lacinia/execute-parsed-query-async`.

Lacinia can now, optionally, collect timing information about
field resolver functions. This information is returned in the
`:extensions :timings` key of the response.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/3?closed=1)

## 0.15.0 -- 19 Apr 2017

Field resolvers can now operate synchronously or asynchronously.

This release fleshes out the Lacinia type system to be fully compliant with the
GraphQL type system.
Previously, there were significant limitations when combining `list` and `non-null` modifiers on types.

The internal representation of enum values has changed from String to Keyword.
You will now see a Keyword, not a String, supplied as the value of an enum-typed argument
or variable.
You may need to make small changes to your field resolvers.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/2?closed=1)


## 0.14.0 -- 29 Mar 2017

This release adds some very small performance improvements.

Field resolver functions may now return sets (where the schema type is a list).
Previously this generated a runtime error.

There is a change to the signature of the
`com.walmartlabs.lacinia.executor/execute-query` function
that will not affect the majority of users.

We have removed an unused dependency on `org.clojure/tools.macro`.

And, of course, smaller fixes and improvements to the documentation.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/1?closed=1)


## 0.13.0 -- 15 Mar 2017

Lucky 13 is our first publicly available version of Lacinia.
It is still alpha and still subject to change, however.
