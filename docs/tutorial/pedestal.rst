Lacinia Pedestal
================

Working from the REPL is important, but ultimately GraphQL exists to provide a web-based API.
Fortunately, it is very easy to get your Lacinia application up on the web, on top of
the `Pedestal <http://pedestal.io/>`_ web tier, using
the `lacinia-pedestal <https://github.com/walmartlabs/lacinia-pedestal>`_ library.

In addition, for free, we get GraphQL's own REPL: `GraphiQL <https://github.com/graphql/graphiql>`_.

Add Dependencies
----------------

.. literalinclude:: /_examples/tutorial/deps-4.edn
   :caption: deps.edn
   :emphasize-lines: 4-5

We've added two libraries: ``lacinia-pedestal`` and ``io.aviso/logging``.

The former brings in quite a few dependencies, including Pedestal, and the underlying
`Jetty <https://www.eclipse.org/jetty/>`_ layer that Pedestal builds upon.

The ``io.aviso/logging`` library sets up
`Logback <https://logback.qos.ch/>`_ as the logging library.

Clojure and Java are both rich with web and logging frameworks; Pedestal and Logback are simply particular
choices that we've made and prefer; many other people are using Lacinia on the web without
using Logback `or` Pedestal.

Some Configuration
------------------

For best results, we can configure Logback; this keeps startup and request handling
from being very chatty:

.. literalinclude:: /_examples/tutorial/logback-test-1.xml
   :caption: dev-resources/logback-test.xml


This configuration hides log events below the warning level (that is, debug and info events).
If any warnings or errors do occur, minimal output is sent to the console.

A :file:`logback-test.xml` takes precendence over the production :file:`logback.xml` configuration
we will eventually supply.

User Namespace
--------------

We'll add more scaffolding to the ``user`` namespace, to make it possible to start and stop
the Pedestal server.

.. literalinclude:: /_examples/tutorial/user-3.clj
   :caption: dev-resources/user.clj
   :emphasize-lines: 3-6,34-

This new code is almost entirely boilerplate for Pedestal and for Lacinia-Pedestal.
The core function is ``com.walmartlabs.lacinia.pedestal2/default-service`` [#whytwo]_ which is passed the compiled schema
and a map of options, and returns a Pedestal service map which is then used
to create the Pedestal server.

.. sidebar:: default-service is only temporary

   If you check the API documentation for ``default-service``, you'll
   see that it is intended as a quick option when first starting, as here.
   You'll almost certainly want to replace it with more specific calls to
   other functions in the ``pedestal2`` namespace that more precisely
   address your application's particular functional and security needs.

By default, incoming  GraphQL POST requests are handled at the ``/api`` path.
The default port is 8888. We'll get to the details later.

The ``/ide`` path (which is opened at startup), and related JavaScript and CSS resources, can only be accessed
when GraphiQL is enabled.


Starting The Server
-------------------

With the above scaffolding in place, it is just a matter of starting the REPL and evaluating ``(start)``.

At this point, your web browser should open to the GraphiQL application:

.. image:: /_static/tutorial/graphiql-initial.png

.. tip::

   It's really worth following along with this section, especially if you haven't played
   with GraphiQL before.
   GraphiQL assists you with formatting, provides pop-up help, flags errors
   in your query,
   and supplies automatic input completion.
   It can even pretty print your query.
   It makes for quite the demo!

Running Queries
---------------

We can now type a query into the large text area on the left and then click
the right arrow button (or type ``Command+Enter``), and see the server response as pretty-printed JSON on the right:

.. image:: /_static/tutorial/graphiql-basic-query.png

Notice that the URL bar in the browser has updated: it contains the full query string.
This means that you can bookmark a query you like for later (though it's easier to access prior
queries using the the ``History`` button).

Importantly, you can copy that URL and provide it to other developers.
They can start up the application on their workstations and see exactly what you see, a real boon for
describing and diagnosing problems.

This approach works even better when you keep a GraphQL server running on a shared staging server.
On split [#split]_ teams, the developers creating the application can easily explore the interface exposed
by the GraphQL server, even before writing their first line of client-side code.

Trust me, they love that.

You'll notice that the returned map is in JSON format, not EDN, and that it includes a lot more information in the ``extensions`` key.  This is optional :doc:`tracing information <../tracing>`, where Lacinia identifies how it spent all the time processing the request.  This is an example of something that's automatic when using ``default-service`` that you'll definitely want to turn off in production.

Documentation Browser
---------------------

The ``< Docs`` button on the right opens the documentation browser:

.. image:: /_static/tutorial/graphiql-doc-browser.png

The documentation browser is invaluable: it allows you to navigate around your schema, drilling down
to objects, fields, and types to see a summary of each
declaration, as well as documentation - those
``:description`` values we added way back
:doc:`at the beginning <init-schema>`.

Take some time to learn what GraphiQL can do for you.


Summary
-------

It takes very little effort, just a dependency change and a little boilerplate code, to expose our little application to the web, and along the way, we gain access to the powerful GraphiQL IDE.

Next up, we'll look into reorganization our code for later growth by adding a layer of components atop our code.


.. [#whytwo] Why ``pedestal2``?  The initial version of lacinia-pedestal had a slightly different
   approach to setting up Pedestal that proved to be problematic, it also supported some outdated
   ideas about how to process incoming requests.
   For compatibility, the
   original namespace, ``com.walmartlabs.lacinia.pedestal`` was left functionally s-is, but a new namespace,
   ``pedestal2`` was created to address the concerns.

.. [#split] That is, where one team or set of developers `just` does the user interface,
   and the other team `just` does the server side (including Lacinia). Part of the
   value proposition for GraphQL is how clean and uniform this split can be.
