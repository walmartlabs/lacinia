ADR-001: Error Structure
========================

Context
-------

GraphQL should be commended for making errors a first-class citizen along with data.
This has provided a great deal of structure in reporting errors to clients.

However, the semantics of errors has been less than ideal.
In some clients, errors are fully ignored unless the overall request status is not
in the 200 range.
In other clients, the presence of any errors is treated as a fatal error on the entire
operation.

The specification's details about errors apply largely to errors caught before query
execution (such has unparsable documents) and a few specific cases that occur
during execution (null data provided by a field resolver for a non-nullable field).

However, errors can and should also be a way for servers to provide
advice to the client concerning otherwise well-formed queries:

- Authentication and authorization failures
- Missing data (due to inavailablilty of back end systems)
- Invalid request data (e.g., a string that fails to conform to a particular regular expression)

Of these, the first and last might be considered fatal, and the missing data
scenario is deserving of a warning, a warning that may further clarify (for example)
why certain expected fields are null.

Further, short of a lot of guess work in the error message, there isn't a clear
way for clients to know specifically what failed on the server.
It should be clear to clients which errors are advisory (e.g., missing data)
and which are fatal (e.g., authorization failure).

Decision
--------

Lacinia shall define two additional error map keys: ``:error`` and ``:severity``.
As per the June 2018 spec, these will be placed in the ``:extensions`` key.

.. code-block:: clojure

     {:errors [{:message "Unable to retrieve pizza toppings."
                :locations ...
                :path [:get_pizza :toppings]
                :extensions
                {:error :toppings_unavailable
                 :severity :dataloss}}]
       :data ... }


Error Names
~~~~~~~~~~~

The ``:error`` extension is a simple error name.
By convention, the error name is a lower-case GraphQL identifier.

Lacinia will define the following error names:

- ``:parse_failure`` - the requested document could not be parsed, or the document
   references unknown types, fields, or arguments
- ``:missing_operation`` - the document does not contain the requested operation
- ``:type_error`` - data supplied as an argument value was not type compatible with an argument
- ``:scalar_error`` - a scalar value could not be converted from external (string) representation
  to internal type, or vice-versa
- ``:validation`` - An input argument failed input validation (for example, a null value provided to a
   non-null argument)
- ``:unknown`` - placeholder for any other kind of error where one is not provided

The ``:validation`` error is typically associated with application logic that performs further checks of arguments,
such as enforcing minimum or maximum values, or checking that a string conforms to a regular expression.

Applications may define their own error names.

Field documentation should liberally identify what errors may be associated
with which fields.

Errors and error names are a primary path of communication between front-end and back-end developers.
It should always be clear what behavior is expected of the client in light of any particular
combination of error names and error severities in the GraphQL response.

Error Severity
~~~~~~~~~~~~~~

Severity is used to describe, to the client, how much processing
of the request took place, and if and how to present the failure
to the end user.

* ``:warn`` - A general warning
* ``:dataloss`` - A specific warning: requested data may not be present in the result
* ``:fatal`` - Processing was either unable to begin, or incomplete/incorrect results were obtained

For example, a warning might be used to indicate that some data was obtained from a cache
because a backend system failed to respond.

A dataloss might be added in other scenarios where requested data could
not be obtained from a backend and was omitted.

Response Status
~~~~~~~~~~~~~~~

The ``:status`` key represents an HTTP status for the overall response, if
present.

In the presense of multiple ``:status`` value, the numerically largest is chosen.

The ``:status`` key is stripped out of the ``:extensions`` map before
the response is delivered to the client.

Defaults
~~~~~~~~

An error map that does not specify an error will be defaulted to ``:UNKNOWN``.
An error map that does not specify a severity will be defaulted to ``:warn``.

Status
------

Proposed.

Consequences
------------

A change to the structure of error maps in the result may affect
some clients that looks for particular structures in the error maps;
this is somewhat unavoidable with the switch to
error maps as specified in the June 2018 release of the GraphQL specification,
which mandates the use of a ``extensions`` object to contain any
but the few spec-defined keys.

Responses that contain errors will be slightly larger, due to the extra
keys (``:error`` and ``:severity``) that are added, which further ensures
that every error map will have a ``:extensions`` key.


