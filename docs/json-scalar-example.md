# Example: Raw JSON Scalar in Lacinia

## Motivation

It is common in GraphQL APIs to need a field that can accept or return arbitrary JSON data. Lacinia does not provide a built-in JSON scalar, but it is straightforward to define one. This example demonstrates how to implement and use a raw JSON scalar in your Lacinia schema.
## Recommendations and Warnings

- Always validate incoming JSON data to avoid security issues and unexpected errors.
- Consider restricting the structure of the JSON if possible, to make your API more predictable.
- Document clearly which fields use the JSON scalar and what kind of data is expected.
- Be aware that large or deeply nested JSON objects may impact performance.
## Example GraphQL Query and Response

Suppose you have a field in your schema that returns a JSON value:

```edn
{:objects
 {:Root
  {:fields
   {:config {:type :JSON}}}}}
```

You can query this field as follows:

```graphql
query {
  config
}
```

And the response might look like:

```json
{
  "data": {
    "config": {
      "enabled": true,
      "threshold": 42,
      "options": ["a", "b", "c"]
    }
  }
}
```
## Implementing the Scalar Functions in Clojure

You can use the [cheshire](https://github.com/dakrone/cheshire) library to parse and generate JSON in Clojure. Here is an example implementation:

```clojure
(ns my-app.json-scalar
  (:require [cheshire.core :as json]))

(defn parse-json [value]
  (try
    (json/parse-string value true)
    (catch Exception _
      nil)))

(defn serialize-json [value]
  (json/generate-string value))
```

Make sure to add `cheshire` to your dependencies in `deps.edn`:

```clojure
;; deps.edn
{:deps {cheshire {:mvn/version "5.11.0"}}}
```
type Product {
  id: ID!
  metadata: JSON
}

type Query {
  getProduct(id: ID!): Product
...existing code...
