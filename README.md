# Lacinia


[![Clojars Project](https://img.shields.io/clojars/v/com.walmartlabs/lacinia.svg)](https://clojars.org/com.walmartlabs/lacinia)
[![CI](https://github.com/walmartlabs/lacinia/actions/workflows/config.yml/badge.svg)](https://github.com/walmartlabs/lacinia/actions/workflows/config.yml)

[Lacinia Manual](http://lacinia.readthedocs.io/en/latest/) |
[Lacinia Tutorial](http://lacinia.readthedocs.io/en/latest/tutorial) |
[API Documentation](http://walmartlabs.github.io/apidocs/lacinia/)

This library is a full implementation of
Facebook's [GraphQL specification](https://facebook.github.io/graphql).

Lacinia should be viewed as roughly analogous to the
[official reference JavaScript implementation](https://github.com/graphql/graphql-js/).
In other words, it is a backend-agnostic GraphQL query execution engine.
Lacinia is not an Object Relational Mapper ... it's simply the implementation of a contract
sitting between the GraphQL client and your data.

Lacinia features:

- An [EDN](https://github.com/edn-format/edn)-based schema language, or use
  GraphQL's [Interface Definition Language](http://spec.graphql.org/June2018/#sec-Type-System).

- High performance parser for GraphQL queries, built on [Antlr4](http://www.antlr.org/).

- Efficient and asynchronous query execution.

- Full support for GraphQL types, interfaces, unions, enums, input objects, and custom scalars.
- Union types in SDL now support an optional leading vertical bar (|) before the first member, following the GraphQL specification. For example:

  ```graphql
  union Searchable =
    | Business
    | Employee
  ```

- Full support for GraphQL subscriptions.

- Full support of inline and named query fragments.

- Full support for GraphQL Schema Introspection.

Lacinia has been developed with a set of core philosophies:

- Prefer data over macros and other tricks: Compose your schema in whatever mix of data and code works for you.

- Embrace Clojure: Use EDN data, keywords, functions, and persistent data structures.

- Keep it simple: You provide the schema and a handful of functions to resolve data, and Lacinia does the rest.

- Do the right thing: apply reasonable defaults without a lot of "magic".

This library can be plugged into any Clojure HTTP pipeline.
The companion library [lacinia-pedestal](https://github.com/walmartlabs/lacinia-pedestal) provides
full HTTP support, including GraphQL subscriptions, for [Pedestal](https://github.com/pedestal/pedestal).

An externally developed library, [duct-lacinia](https://github.com/kakao/duct-lacinia), provides similar capability
for [Duct](https://github.com/duct-framework/duct).

## Getting Started

For more detailed documentation, [read the manual](http://lacinia.readthedocs.io/en/latest/).

### Using as a Dependency

When using Lacinia as a dependency, you may need to prepare the library:

```bash
clojure -X:deps prep
```

This is required because Lacinia uses precompiled ANTLR parsers that need to be built before use.

GraphQL starts with a schema definition of types that can be queried.

A schema starts as an EDN file; the example below demonstrates a small subset
of the available options:

```clojure
{:enums
 {:Episode
  {:description "The episodes of the original Star Wars trilogy."
   :values [:NEWHOPE :EMPIRE :JEDI]}}

 :objects
 {:Droid
  {:fields {:id {:type Int}
            :primaryFunctions {:type (list String)}
            :name {:type String}
            :appearsIn {:type (list :Episode)}}}

  :Human
  {:fields {:id {:type Int}
            :name {:type String}
            :homePlanet {:type String}
            :appearsIn {:type (list :Episode)}}}
  :Query
  {:fields {:hero {:type (non-null :Human)
                   :args {:episode {:type :Episode}}}
            :droid {:type :Droid
                    :args {:id {:type String 
                                :default-value "2001"}}}}}}}
```
The fields of the special Query object define the query operations available; with this schema,
a client can find the Human `hero` of an episode, or find a `Droid` by its id.

A schema alone describes what data is available to clients, but doesn't identify where
the data comes from; that's the job of a field resolver.

A field resolver is just a function which is passed the application context,
a map of arguments values, and a resolved value from a
parent field.
The field resolver returns a value consistent with the type of the field; most field resolvers
return a Clojure map or record, or a list of those.  Lacinia then uses the GraphQL query to 
select fields of that value to return in the response.

Here's what a very opinionated `get-hero` field resolver might look like:

```clojure
(defn get-hero 
  [context arguments value]
  (let [{:keys [episode]} arguments]
    (if (= episode :NEWHOPE)
      {:id 1000
       :name "Luke"
       :homePlanet "Tatooine"
       :appearsIn ["NEWHOPE" "EMPIRE" "JEDI"]}
      {:id 2000
       :name "Lando Calrissian"
       :homePlanet "Socorro"
       :appearsIn ["EMPIRE" "JEDI"]})))
```

In this greatly simplified example, the field resolver can simply return the resolved value.
Field resolvers that return multiple values return a list, vector, or set of values.

In real applications, a field resolver might execute a query against a database,
or send a request to another web service.

After injecting resolvers, it is necessary to compile the schema; this
step performs validations, provides defaults, and organizes the schema
for efficient execution of queries.

This needs only be done once, in application startup code:


```clojure
(require '[clojure.edn :as edn]
         '[com.walmartlabs.lacinia.util :refer [inject-resolvers]]
         '[com.walmartlabs.lacinia.schema :as schema])

(def star-wars-schema
  (-> "schema.edn"
      slurp
      edn/read-string
      (inject-resolvers {:Query/hero get-hero
                         :Query/droid (constantly {})})
      schema/compile))
```

With the compiled application available, it can be used to execute
requests; this typically occurs inside a Ring handler function:

```clojure
(require '[com.walmartlabs.lacinia :refer [execute]]
         '[clojure.data.json :as json])

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (let [query (get-in request [:query-params :query])
               result (execute star-wars-schema query nil nil)]
           (json/write-str result))})
```

Lacinia doesn't know about the web tier at all, it just knows about
parsing and executing queries against a compiled schema.
A companion library, [lacinia-pedestal](https://github.com/walmartlabs/lacinia-pedestal),
is one way to expose your schema on the web.

Clients will typically send a JSON POST request, with a `query` key containing the GraphQL query document:

```
{
  hero {
    id
    name
  }
}
```


The `execute` function returns EDN data that can be easily converted to JSON.
The :data key contains the value requested for the `hero` query in the request.

```clojure
{:data
  {:hero {:id 2000
          :name "Lando Calrissian"}}}
```

This example request has no errors, and contained only a single query.
GraphQL supports multiple queries in a single request.
There may be errors executing the query, Lacinia will process as much as
it can, and will report errors in the :errors key.

One of the benefits of GraphQL is that the client has the power to rename
fields in the response:

```
{
  hero(episode: NEWHOPE) {
    movies: appearsIn
  }
}
```

```clojure
{:data {:hero {:movies [:NEWHOPE :EMPIRE :JEDI]}}}
```

This is just an overview, far more detail is available
in [the manual](http://lacinia.readthedocs.io/en/latest/).

## Status

This library has been used in production at Walmart since 2017, going through a very long
beta period as it evolved; we transitioned to a 1.0 release on 9 Oct 2021.

To use this library with Clojure 1.8, you must include 
a dependency on [clojure-future-spec](https://github.com/tonsky/clojure-future-spec).

More details are [in the manual](http://lacinia.readthedocs.io/en/latest/clojure.html).

## Development

Lacinia uses ANTLR-generated parsers for GraphQL query and schema parsing.

### Building from Source

Compile the Java sources:

```bash
clojure -T:build compile-java
```

This compiles the ANTLR-generated parser classes into `target/classes`.

### Regenerating Parsers

If you modify the ANTLR grammar files (`.g4`), regenerate the Java parsers:

```bash
./codegen.sh
```

### Running Tests

```bash
clojure -X:dev:test
```

## License

Copyright © 2017-2025 WalmartLabs

Distributed under the Apache License, Version 2.0.
