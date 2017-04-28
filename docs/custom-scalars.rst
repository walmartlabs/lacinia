Custom Scalars
==============

Defining custom scalars may allow users to better model their domain.

.. sidebar:: GraphQL Spec

   Read about `custom scalars <https://facebook.github.io/graphql/#sec-Scalars>`_.


To define a custom scalar, you must provide implementations, in your schema, for two operations:

parse
  parses query arguments and coerces them into their scalar types according to the schema.

serialize
  serializes a scalar to the type that will be in the result of the query or mutation.

In other words, a scalar is serialized to another type, typically a string, as part of executing a query
and generating results.
In some cases, such as field arguments, the reverse may be true: the client will provide the serialized version
of a value, and the parse operation will convert it back to the appropriate type.

These operations are implemented in terms of a `clojure.spec conformer <http://clojure.github.io/clojure/branch-master/clojure.spec-api.html#clojure.spec/conformer>`_.

Dates are a common example of this, as dates are not supported directly in JSON, but must always be encoded as
some form of string.

Here is an example that defines and uses a custom ``:Date`` scalar type:

.. literalinclude:: _examples/custom-scalars.edn
   :language: clojure

.. warning::

   This is just an simplified example used to illustrate the broad strokes. It is not thread safe, because
   the ``SimpleDateFormat`` class is not thread safe. It does not properly report
   unparseable values.

The function ``com.walmartlabs.lacinia.schema/as-conformer`` is an easy way to wrap a function as a conformer.

