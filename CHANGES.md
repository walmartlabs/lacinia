## 0.16.0 -- UNRELEASED

## 0.15.0 -- 19 Apr 2017

Field resolvers can now operate synchronously or asynchronously.

This release fleshes out the Lacinia type system to be fully compliant with the
GraphQL type system.
Previously, there were significant limitations when combining `list` and `not-null` modifiers on types.

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
