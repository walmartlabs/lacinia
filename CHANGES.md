## 0.15.0 -- UNRELEASED

This release fleshes out the Lacinia type system to be fully compliant with the
GraphQL type system.
Previously, there were significant limitations when combining `list` and `not-null` modifiers on types.

The internal representation of enum values has changed from string to keyword.
You will now see a keyword, not a string, supplied as the value of an enum-typed argument
or variable.


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
