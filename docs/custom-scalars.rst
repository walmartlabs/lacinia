Custom Scalars
==============

Defining custom scalars may allow users to better model their domain.

.. sidebar:: GraphQL Spec

   Read about :spec:`custom scalars <Scalars>`.


To define a custom scalar, you must provide implementations, in your schema, for two transforming callback functions:

parse
  parses query arguments and coerces them into their scalar types according to the schema.

serialize
  serializes a scalar to a value that will be in the result of the query or mutation.

In other words, a scalar is serialized to another type, typically a string, as part of executing a query
and generating results.
In some cases, such as field arguments, the reverse may be true: the client will provide the serialized version
of a value, and the parse operation will convert it back to the appropriate type.

Both a parse function and a serialize function must be defined for each scalar type.
These callback functions are passed a value and peform necessary coercions and validations.

Neither callback is ever passed ``nil``.

Dates are a common example of this, as dates are not supported directly in JSON, but are typically encoded as
some form of string.

Here is an example that defines and uses a custom ``:Date`` scalar type:

.. literalinclude:: _examples/custom-scalars.edn
   :language: clojure

.. warning::

   This is just an simplified example used to illustrate the broad strokes. It is not thread safe, because
   the ``SimpleDateFormat`` class is not thread safe.


Parsing
-------

The parse callback is provided a value the originates in either the GraphQL query document, or in the
variables map.

The values passed to the callback may be strings, numbers, or even maps (with keyword keys).
It is expected that the parse function will do any necessary conversions and validations, or indicate
an invalid value.

Serializing
-----------

Serializing is often the same as parsing (in fact, it is not uncommon to use one function for both roles).

The serialize callback is passed whatever value was selected from a field and cooerces it to an appropriate
value for the response (typically, either a string, or another value that can be encoded into JSON).

Handling Invalid Values
-----------------------

Especially when parsing an input string into a value, there can be problems, especially included
invalid data sent in the request.

Values may not always be parsable or serializable: a faulty client may pass incorrect data into Lacinia to be parsed, or a programming
error may cause a mismatch when serializing.

The simplest way to indicate a parse or serialize failure is to simply return nil.
Lacinia will create a generic error map and add that to the response.

Alternately, a parse or serialize callback can throw an exception; the message of the exception can provided more details
about what failed,
and the ``ex-data`` of the exception will be merged into the error map.

For example, a ``Date`` scalar may use a ``java.time.format.DateTimeFormatter`` to parse a string, and
may catch an exception from the call to ``parse`` and supply a more user-friendly exception detailing the expected format.

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
:api:`util/attach-scalar-transformers`.

