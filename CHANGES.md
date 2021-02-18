## 0.39 - UNRELEASED

A new function, `com.walmartlabs.lacinia.schema/select-type`, allows a type within the
compiled schema to be selected; this exposes the type, fields, arguments, and so forth,
much like GraphQL introspection, but at the Clojure API level.

Some additions and incompatible changes have been made to the protocols in the `com.walmartlabs.lacinia.selection`;
these protocols are not meant to be implemented except by the library, and prior code (from 0.38) will be
largely source compatible with the new names.  Further such changes are expected.

## 0.38.0 -- 22 Jan 2021

Optional request tracing is now designed to be compatible with Apollo GraphQL's implementation.

New support for [Apollo GraphQL Federation](https://www.apollographql.com/docs/apollo-server/federation/introduction/).

The default objects names for storing operations are now `Query`, `Mutation`,
and `Subscription`, and these must be objects (not unions), as
per [the GraphQL specification](http://spec.graphql.org/June2018/#sec-Root-Operation-Types).

Added function `com.walmartlabs.lacinia.executor/selection` which provides access to 
the details about the selection, including directives and nested selections.

A new schema compilation option can be used to implement field definition directives by wrapping
field resolvers; the `selection` API can expose information about the field, including a field's
type system directives. 

Fixed an issue where a Schema Definition Language that contained
the literal values `true`, `false`, or `null` would fail to parse.

Lacinia now correctly conforms to the GraphQL specification related to 
[Errors and Non-Nullability](https://spec.graphql.org/June2018/#sec-Errors-and-Non-Nullability).

It is now possible to use query variables inside a list or input object type.

## 0.37.0 -- 30 Jun 2020

Added new function `com.walmartlabs.lacinia.util/inject-streamers`, used to
add streamers to a schema (typically, one parsed from a GraphQL schema document).
Likewise, `inject-scalar-transformers` injects :parse and :serialize keys into a
scalar (complementing the `inject-enum-transformers` function added in 0.36.0).

Combined, these functions (plus `inject-descriptions`) replace the `attach` argument
to `com.walmartlabs.lacinia.parser.schema/parse-schema` which is now deprecated.

The schema parser has been updated, to allow input values (within input types)
to specify defaults, and to support extending input types.

The preview API functions (`selections-seq2`, etc.) now recognize the `@include` and
`@skip` directives.

Exceptions thrown by resolver functions are now caught and wrapped as new exceptions
that identify the field, query location and path, and field arguments.
This also applies to exceptions thrown when processing the result, such as an invalid
enum value returned from a resolver function.

Resolver functions can now return maps whose values are themselves ResolverResults.
This can, in some cases, be easier than coordinating a parent resolver and a nested resolver.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/25?closed=1)

## 0.36.0 -- 13 Feb 2020

It is now possible to provide `:parse` and `:serialize` functions for
enum types, allowing a mapping between your application's model and
the GraphQL model.

Capturing of lacinia resolver execution times has been significantly
changed in an incompatible way (this feature is still experimental).

The Lacinia execution model has changed slightly to support setting a timeout
on execution; most of the work now executes in a different thread.
We've also increased Lacinia's ability to split work across multiple threads, along
with some tiny performance improvements.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/24?closed=1)

## 0.35.0 -- 12 Sep 2019

Lacinia now uses the fully qualified field and argument name as `:extension` data
when reporting errors in fields, field arguments, and variables.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/23?closed=1)

## 0.34.0 -- 16 Aug 2019

The new function `com.walmartlabs.lacinia.executor/parsed-query->context`
makes it possible to use the preview API functions in the executor
namespace even before the query is executed, for example, from 
a Pedestal interceptor.

New preview API function `com.walmartlabs.lacinia.executor/selections-seq2`
improves on `selections-seq`, returning the name, alias, and arguments of
each selected field.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/22?closed=1)

## 0.33.0 -- 23 May 2019

GraphQL Schema Definition Language documents can now contain empty types, and use
`extend type`.

The handling of nulls, omitted variables and arguments, and default values
has changed to adhere to the latest version of the specification.
Non-null arguments may now have defaults, and nullable variables may be provided
to non-null arguments (though a null value will cause an exception prior to
query execution).

It is now possible to compile a schema with introspection disabled; this may
be useful in production in some cases (though it prevents tools such as
GraphiQL from operating).

### **Breaking Change**: com.walmartlabs.lacinia.executor/selections-tree

`selections-tree` has changed; the value for a key is now a vector of nodes
(each possibly nil) rather than just a single node; this is to account for
the fact that a selection set may reference the same field, using different
aliases (and different arguments and sub-selections).

### **Breaking Change**: Scalars

The built-in scalar types have been tightened up to more closely match the
spec; this means that some previously allowed input values (in a request)
or raw values (as provided by field resolvers) may now generate an error; for
example, previously, the `Int` scalar could parse a string value into an integer.

Remember it is possible to override built-in scalar types; if you
have clients that provide unsupported data (such as strings for `Int` fields),
you can provide an override of any built-in scalar to be more forgiving.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/21?closed=1)

## 0.32.0 -- 4 Feb 2019

Added a schema compile option, `:promote-nils-to-empty-list?`, which converts
nils resolved from list fields back into empty lists (use only if you
have existing clients that break when they see those nils).

The `com.walmartlabs.schema/coercion-failure` function has been deprecated,
scalar parse and serialize functions can simply throw an exception rather
than invoke `coercion-failure`.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/20?closed=1)

## 0.31.0 -- 4 Jan 2019

This release, regrettably, has some backwards incompatible changes (notice the `0.` at the front
of the version number).
The necessary changes to existing applications are expected to be minor and localized.
 
### **Breaking Change**: Schema Definition Language Documents

Previous releases of the Schema Definition Language parser
wrapped the entire document with `{` and `}`.
This is not correct, and such documents are not valid SDL.

This release fixes that, but it means that any existing SDL documents will not
parse correctly until the outermost curly braces are removed.

### **Breaking Change**: Incompatible changes to scalars

This release revamps how custom scalars are implemented.
These changes make scalars more flexible, allowing for use cases
that were previously not possible.

- The :parse and :serialize callbacks for scalars are no longer clojure.spec conformers, but are simple functions
- The :parse callback may now be passed non-strings, such as numbers or even maps
- The callbacks should not throw an exception, but should invoke
  `com.walmartlabs.lacinia.schema/coercion-failure`.
- Callbacks may also return nil to indicate a coercion failure

Schemas that use custom scalars will not compile until the scalars are
updated (you'll see a clojure.spec validation exception).

### Other changes

Limited support for custom directives.

This release replaces the dependency on
[org.flatland/ordered](https://github.com/amalloy/ordered)
with vendored copies of the code in an internal namespace.
This is to enable compatibility with JDK 11.

Previously, there were places where a field resolver might return nil for
a nullable list, and the nil was converted to an empty list.
That no longer happens, the nil stays nil.

Added new resolver result modifiers, ``com.walmartlabs.lacinia.resolve/with-extensions``
and ``with-warning``.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/19?closed=1)


## 0.30.0 -- 1 Oct 2018

A field resolver that returns a list of values may now wrap the individual
items in the list with `com.walmartlabs.lacinia.resolve/with-context`
(or `with-error`).

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/18?closed=1)

## 0.29.0 -- 14 Sep 2018

Changes have started to bring Lacinia into compliance with
the [June 2018 version of the GraphQL specification](https://github.com/facebook/graphql/releases/tag/June2018).

Lacinia now supports block strings (via `"""`) in query and schema documents.

In addition, descriptions are now supported inside schema documents;
a string (or block string) before an element in the schema becomes the
documentation for that element.

The error maps inside the `:error` key are now structured according to the June 2018 spec;
the top level keys are `:message`, `:locations`, `:path`, and
`:extensions` (which contains any other keys in the error map supplied
by the field resolver).

The behavior of the `:scalars` option to `com.walmartlabs.lacina.parser.schema/parse-schema`
has changed slightly; the values provided are now merged with any scalar
data defined in the schema document.
Previously, the supplied value *overwrote* what was parsed.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/17?closed=1)

## 0.28.0 -- 21 Jun 2018

Removed a potential race condition related to asynchronous field resolvers.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/16?closed=1)

## 0.27.0 -- 6 Jun 2018

A change to how GraphQL schema documentation is attached.
Previously, arguments were refered to as `:MyType.my_field/arg_name`
but with this release, we've changed it to `:MyType/my_field.arg_name`.

It is now possible, when parsing a schema from SDL via
`com.walmartlabs.lacinia.parser.schema/parse-schema`, to
attach descriptions to interfaces, enums, scalars, and unions.
Previously, only objects and input objects could have descriptions attached.

New function `com.walmartlabs.lacinia.util/inject-resolvers` is an alternate way
to attach resolvers to a schema.

It is now possible to combine external documentation, from a Markdown file,
into an EDN schema.
See `com.walmartlabs.lacinia.parser.docs/parse-docs` and
[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/16?closed=1)
`com.walmartlabs.lacinia.util/inject-descriptions`.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/15?closed=1)

## 0.26.0 -- 20 Apr 2018

Lacinia now supports the `:roots` key in the input schema, which makes
it possible to define query, mutation, or subscription operations
in terms of the fields of an explicitly named object in the schema.
This aligns Lacinia better with other implementations of GraphQL.

Lacinia is now based on Clojure 1.9, though it can also be used with
Clojure 1.8.

Added the `com.walmartlabs.lacinia.parser/summarize-query` function,
which is used to summarize a query without distractions such as
aliases and field arguments. This is used to group similar
queries together when doing performance analysis.

Query parsing logic has been rewritten entirely, for performance
and maintenance reasons.
As a side-effect, location information for query errors
is more accurate.

[Closed Issues](https://github.com/walmartlabs/lacinia/milestone/14?closed=1)

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
""
Field resolvers for enum types are now required to return a keyword, and that
keyword must match one of the values defined for the enum.
Previously, Lacinia failed to perform any checks in this case, which could result
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
