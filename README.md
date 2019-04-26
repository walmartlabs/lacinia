# Lacinia


[![Clojars Project](https://img.shields.io/clojars/v/com.walmartlabs/lacinia.svg)](https://clojars.org/com.walmartlabs/lacinia)

[![CircleCI](https://circleci.com/gh/walmartlabs/lacinia/tree/master.svg?style=svg)](https://circleci.com/gh/walmartlabs/lacinia/tree/master)

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

- An [EDN](https://github.com/edn-format/edn)-based schema language.

- High performance parser for GraphQL queries, built on [Antlr4](http://www.antlr.org/).

- Efficient and asynchronous query execution.

- Full support for GraphQL types, interfaces, unions, enums, input objects, and custom scalars.

- Full support for GraphQL subscriptions.

- Full support of inline and named query fragments.

- Full support for GraphQL Schema Introspection.

Lacinia has been developed with a set of core philosophies:

- Prefer data over macros and other tricks. Compose your schema in whatever mix of data and code works for you.

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

GraphQL starts with a schema definition of exposed types.

A schema starts as an EDN file; the example below demonstrates a small subset
of the available options:

```clojure
{:enums
 {:episode
  {:description "The episodes of the original Star Wars trilogy."
   :values [:NEWHOPE :EMPIRE :JEDI]}}

 :objects
 {:droid
  {:fields {:primary_functions {:type (list String)}
            :id {:type Int}
            :name {:type String}
            :appears_in {:type (list :episode)}}}

  :human
  {:fields {:id {:type Int}
            :name {:type String}
            :home_planet {:type String}
            :appears_in {:type (list :episode)}}}}

 :queries
 {:hero {:type (non-null :human)
         :args {:episode {:type :episode}}
         :resolve :get-hero}
  :droid {:type :droid
          :args {:id {:type String :default-value "2001"}}
          :resolve :get-droid}}}
```


A schema alone describes what data is available to clients, but doesn't identify where
the data comes from; that's the job of a field resolver, provided by the
:resolve key inside fields such as the :hero and :droid query.

The values here, :get-hero and :get-droid, are placeholders; the startup code
of the application will use
`com.walmartlabs.lacinia.util/attach-resolvers` to attach the actual
field resolver function.

A field resolver is just a function which is passed the application context,
a map of arguments values, and a resolved value from a
parent field.
The field resolver returns a value. If it's a scalar type, it should return a value
that conforms to the defined type in the schema.
If not, it's a type error.

The field resolver is totally responsible for obtaining the data from whatever
external store you use: whether it is a database, a web service, or something
else.

It's important to understand that _every_ field has a field resolver, even if
you don't define it explicitly.  If you don't supply a field resolver,
Lacinia provides a default field resolver, customized to the field.

Here's what the `get-hero` field resolver might look like:

```clojure
(defn get-hero [context arguments value]
  (let [{:keys [episode]} arguments]
    (if (= episode :NEWHOPE)
      {:id 1000
       :name "Luke"
       :home_planet "Tatooine"
       :appears_in ["NEWHOPE" "EMPIRE" "JEDI"]}
      {:id 2000
       :name "Lando Calrissian"
       :home_planet "Socorro"
       :appears_in ["EMPIRE" "JEDI"]})))
```

In this greatly simplified example, the field resolver can simply return the resolved value.
Field resolvers that return multiple values return a list, vector, or set of values.

In real applications, a field resolver might execute a query against a database,
or send a request to another web service.

After attaching resolvers, it is necessary to compile the schema; this
step performs validations, provide defaults, and organizes the schema
for efficient execution of queries.

This needs only be done once, in application startup code:


```clojure
(require '[clojure.edn :as edn]
         '[com.walmartlabs.lacinia.util :refer [attach-resolvers]]
         '[com.walmartlabs.lacinia.schema :as schema])

(def star-wars-schema
  (-> "schema.edn"
      slurp
      edn/read-string
      (attach-resolvers {:get-hero get-hero
                         :get-droid (constantly {})})
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
           (json/write-str result)})
```

Lacinia doesn't know about the web tier at all, it just knows about
parsing and executing queries against a compiled schema.
A companion library, [lacinia-pedestal](https://github.com/walmartlabs/lacinia-pedestal),
is one way to expose your schema on the web.

User queries are provided as the body of a request with the content type `application/graphql`.
The GraphQL query language is designed to look familiar to someone who is versant in JSON.

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
    movies: appears_in
  }
}
```

```clojure
{:data {:hero {:movies [:NEWHOPE :EMPIRE :JEDI]}}}
```

This is just an overview, far more detail is available
in [the manual](http://lacinia.readthedocs.io/en/latest/).

## Status

Although this library is used in production at Walmart, it is
still considered alpha software - subject to change.
We expect to stabilize it in the near future.

To use this library with Clojure 1.8, you must include 
a dependency on [clojure-future-spec](https://github.com/tonsky/clojure-future-spec).

More details are [in the manual](http://lacinia.readthedocs.io/en/latest/clojure.html).

## License

Copyright © 2017-2019 WalmartLabs

Distributed under the Apache License, Version 2.0.

Portions of the code are derived from
the [ordered](https://github.com/amalloy/ordered)
and [useful](https://github.com/amalloy/useful) libraries, which are released under the terms
of the [Eclipse Public License - v 1.0](LICENSE.ordered.txt).
