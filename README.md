# Lacinia


[![Clojars Project](https://img.shields.io/clojars/v/com.walmartlabs/lacinia.svg)](https://clojars.org/com.walmartlabs/lacinia)

[![CircleCI](https://circleci.com/gh/walmartlabs/lacinia/tree/master.svg?style=svg)](https://circleci.com/gh/walmartlabs/lacinia/tree/master)

This library is a full implementation of
the [GraphQL specification](https://facebook.github.io/graphql) and aims to
maintain _external_<sup id="a1">[1](#f1)</sup> compliance with the specification.

It should be viewed as roughly analogous to
the
[official reference JavaScript implementation](https://github.com/graphql/graphql-js/).
In other words, it is a backend-agnostic GraphQL query execution engine.

It provides:

- A *pure data* schema definition DSL. Define the GraphQL schema your server
  exposes using data (a simple EDN file will do). Process and augment your
  schema with ordinary functions prior to handing it to this library. Add
  entry-points (known as _resolvers_) to populate an
  executed data structure based on the query.
- A query parser.  Given a compliant GraphQL query, yield a Clojure data structure.
- A query validator.
- A built-in query execution engine (using a naive serial execution path).
  Given a query and a schema, traverse the query and return a data
  structure. This data structure will typically be serialized to JSON and
  returned. This engine implements a protocol -- because this library is
  backend agnostic, you're free to implement your own and optimize for your
  use-case.

Core philosophies:
- Data, not macros.  Schemas are data and you can manipulate them as such.
- It's impossible for this core library to make
assumptions about every backend it might be running on. Define your own
execution path to optimize backend queries and leverage the underlying
infrastructure (query parsing, validation, schema, and more).
- Webserver agnostic. You can use use this with any Clojure web stack (or not
  with a webserver at all).
- No magic.  Use this for full query execution lifecycle or use the portions
  you want.
- Embrace `clojure.spec` internally and externally.  For instance, user custom
  scalar types are expected to be defined as conformers.

## Getting Started

For more detailed documentation, [read the manual](http://lacinia.readthedocs.io/en/latest/).

GraphQL starts with a schema definition of exposed types.

A schema starts as an EDN file; the example below demonstrates several
of the available options:

```clojure
{:enums
 {:episode
  {:description "The episodes of the original Star Wars trilogy."
   :values ["NEWHOPE" "EMPIRE" "JEDI"]}}

 :objects
 {:droid
  {:fields {:primary_functions {:type (list String)}
            :id {:type Int}
            :name {:type String}
            :appears_in {:type (list :episode)}}}

  :human
  {:fields {:home_planet {:type String}
            :id {:type Int}
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
:resolve key inside files such as the :hero and :droid query.

The values here, :get-hero and :get-droid, are placeholders; the startup code
of the application will use
`com.walmartlabs.lacinia.util/attach-resolvers` to attach the actual
field resolver function.

A field resolver is just a function which is passed the application context,
a map of arguments to values, and a resolved value from a
parent field.
The field resolver returns a value. If it's a scalar type, it should return a value
that conforms to the defined type in the schema.
If not, it's a type error.

The field resolver is totally responsible for obtaining the data from whatever
external store you use: whether it is a database, a web service, or something
else.


It's important to understand that _every_ field has a field resolver, even if
you don't define it.  If you don't define one, Lacinia provides a default field resolver.

Here's what the `get-hero` field resolver might look like:

```
(defn get-hero [context arguments value]
  (let [{:keys [episode]} arguments]
    (if (= episode "NEWHOPE")
      {:id 1000
       :name "Luke"
       :home-planet "Tatooine"
       :appears-in ["NEWHOPE" "EMPIRE" "JEDI"]}
      {:id 2000
       :name "Lando Calrissian"
       :home-planet "Socorro"
       :appears-in ["EMPIRE" "JEDI"]})))
```

The field resolver can simply return the resolved value.
Field resolvers that return multiple values return a seq of values.

After attaching resolvers, it is necessary to compile the schema; this
step performs validations, provide defaults, and organizes the schema
for efficient execution of queries.

This needs only be done once, in application startup code:


```
(require '[com.walmartlabs.lacinia.util :refer [attach-resolvers]]
         '[com.walmartlabs.lacinia.schema :as schema])

(def star-wars-schema
  (-> "schema.edn"
      io/resource
      slurp
      edn/read-string
      (attach-resolvers {:get-hero get-hero
                         :get-droid (constantly {})})
      schema/compile))
```

With the compiled application available, it can be used to execute
requests; this typically occurs inside a Ring handler function:

```
(require '[com.walmartlabs.lacinia :refer [execute]]
         '[clojure.data.json :as json])

(defn handler [request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body
   (->> {:request request}
        (execute my-schema q nil)
        json/write-str)})
```

Lacinia doesn't know about the web tier at all, it just knows about
parsing and executing queries against a compiled schema.

User queries are provided as the body of a request with the content type application/graphql.
It looks a lot like JSON.

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

```
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

```
{:data {:hero {:movies ["NEWHOPE" "EMPIRE" "JEDI"]}}}
```

## Status

Although this library is used internally, in production, it is
still considered alpha software - subject to change.
We expect to stabilize it in the near future.

## License

Copyright © 2017 WalmartLabs

Distributed under the Apache License, Version 2.0.

## Footnotes

<b id="f1">[1]</b> External compliance means that the edges should perform the
same as another GraphQL library, but the internal algorithms to achieve that
result may be different and deviate from specification in order to work in a
functional way. [↩](#a1)
