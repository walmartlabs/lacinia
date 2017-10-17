Custom Scalars
==============

Defining custom scalars may allow users to better model their domain.

.. sidebar:: GraphQL Spec

   Read about :spec:`custom scalars <Scalars>`.


To define a custom scalar, you must provide implementations, in your schema, for two transforming operations:

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
   the ``SimpleDateFormat`` class is not thread safe.

The function ``com.walmartlabs.lacinia.schema/as-conformer`` is an easy way to wrap a function as a conformer.

Handling Invalid Values
-----------------------

Especially when parsing an input string into a value, there can be problems, including invalid user input.

When using ``as-conformer``, any exception thrown by the function will be consumed and converted into ``:clojure.spec/invalid-value``.
Lacinia will generate a default error message, and an error map will be added to the ``:errors`` key of the result.

If you want more control, you can use the function ``com.walmartlabs.lacinia.schema/coercion-failure``, which allows you
to provide a customized message and even additional data for the error map.

Scalars and Variables
---------------------

.. sidebar:: GraphQL Spec

   Read about :spec:`variables <Language.Variables>`.

When using variables, the scalar parser will be provided not with a string per-se, but
with a Clojure value: a native Long, Double, or Boolean. In this case, the parser
is, not so much parsing, as validating and transforming.

For example, the built-in ``Int`` parser handles strings and all kinds of numbers
(including non-integers). It also ensures that ``Int`` values are, as identified in
the :spec:`specification <Int>`, limited to signed
32 bit numbers.

Attaching Scalar Transformers
-----------------------------

As with field resolvers, the pair of transformers for each scalar have no place in an EDN file as they are functions.
Instead, the transformers can be attached after reading the schema from an EDN file, using the function
``com.walmartlabs.lacinia.util/attach-scalar-transformers``.

