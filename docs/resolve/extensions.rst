Extensions
==========

Lacinia makes it possible to add extension data to the result map from within field resolvers.
This extension data is exposed as the ``:extensions`` key on the result map (alongside the
more familiar ``:data`` and ``:errors`` keys).

.. sidebar:: Extensions key?

   GraphQL supports a third result key, ``extensions``, as
   described in :spec:`the spec <Response-Format>`.
   It exists just for this kind of extra information in the response.

The GraphQL specification allow for extensions but leaves them entirely up to the application to define and
use - there's no validation at all.
One example of extension data is :doc:`tracing information </tracing>` that Lacinia can optionally include in
the result.

More general extension data is introduced using modifier functions defined in the ``com.walmartlabs.lacinia.resolve``
namespace.

Extension Data
--------------

The ``with-extensions`` function is used to introduce data into the result.
``with-extension`` is provided with a function, and additional optional arguments, and is used to modify
the extension data to a new state.

A hypothetical example; perhaps you would like to identify the total amount of time spent performing database
access, and return a total in the ``:total-db-access-ms`` extension key.

You might instrument each resolver that accesses the database to calculate the elapsed time.

.. literalinclude:: /_examples/extension.edn
   :language: clojure
   :emphasize-lines: 8-

The call to ``with-extensions`` adds the elapsed time for this call to the key (the ``fnil`` function
lets the ``+`` function treat nil as 0).

This data is exposed in the final result map:

.. literalinclude:: /_examples/extension-result.edn
   :language: clojure
   :emphasize-lines: 5-

You should be aware that field resolvers may run in an unpredictable order, especially when
:doc:`asynchronous field resolvers <async>` are involved.
Complex update logic may be problematic if one field resolver expects to modify extension data introduced
by a different field resolver.
Sticking with ``assoc-in`` or ``update-in`` is a good bet.

Warnings
--------

The ``with-error`` modifier function adds one or more errors to a result; in general, errors are
serious - Lacinia adds errors when it can't parse the GraphQL document, for example.

Lacinia adds a less dramatic level, a warning, via the ``with-warning`` modifier.
``with-warning`` adds an error map to the ``:warnings`` map stored in the ``:extensions`` key.

What a client does with errors and warnings is up to the application.
In general, errors should indicate a failure of the overall request, and an interactive client
might display an alert dialog related to the request and response.

An interactive client may present warnings differently, perhaps adding an icon to the user view
to indicate that there were non-fatal issues executing the query.
