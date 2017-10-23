Lacinia Pedestal
================

Working from the REPL is important, but ultimately GraphQL exists to provide a web-based API.
Fortunately, it is very easy to get your Lacinia application up on the web, on top of
the `Pedestal <http://pedestal.io/>`_ web tier, using
`Lacinia-Pedestal <https://github.com/walmartlabs/lacinia-pedestal>`_.

In addition, for free, we get GraphQL's own REPL: `GraphiQL <https://github.com/graphql/graphiql>`_.

Add Dependencies
----------------

All that's necessary is to add a line to include lacinia-pedestal.

.. ex:: pedestal project.clj
   :emphasize-lines: 8-9

We've added two libraries; ``lacinia-pedestal`` and ``io.aviso/logging``.

The former brings in quite a few dependencies, including Pedestal, and the underlying
`Jetty <https://www.eclipse.org/jetty/>`_ layer that Pedestal builds upon.

The ``io.aviso/logging`` library sets up
`Logback <https://logback.qos.ch/>`_ as the logging library.

Some Configuration
------------------

For best results, we can configure Logback; this keeps startup and request handling
from being very chatty:

.. ex:: pedestal dev-resources/logback-test.xml

A :file:`logback-test.xml` takes precendence over the production :file:`logback.xml` configuration
we will eventually supply.

User Namespace
--------------

We'll add more scaffolding to the ``user`` namespace, to make it possible to start and stop
the Pedestal server.

.. ex:: pedestal dev-resources/user.clj
   :emphasize-lines: 5-7,35-

The changes are generally boilerplate for Pedestal and for Lacinia-Pedestal.
The core function is ``pedestal-service`` which is passed the compiled schema
and a map of options, and returns a Pedestal service map which is then used
to define the Pedestal server.

GraphiQL is not enabled by default; it is opt-in, and should generally only be enabled
for development servers, or behind a firewall that limits access from the outside world.

Lacinia-Pedestal services GraphQL requests at the ``/graphql`` path.
It handles both GET and POST requests. We'll get to the details later.

The ``/``, ``/index.html``, and related JavaScript and CSS resources can only be accessed
when GraphiQL is enabled.


Starting The Server
-------------------

With the above scaffolding in place, it is just a matter of starting the REPL and evaluating ``(start)``.

At this point, your web browser should open to the GraphiQL application:

.. image:: /_static/tutorial/graphiql-initial.png

.. tip::

   It's really worth following along with this section, especially if you haven't played
   with GraphiQL before. GraphiQL assists you formatting, pop-up help, flagging of errors,
   and automatic completions.

We can now type a query into the large text area on the left, and see pretty-printed JSON on the right:

.. image:: /_static/tutorial/graphiql-basic-query.png

Notice that the URL bar in the browser has updated: it contains the full query string.
This means you can bookmark a query you like for later (though it's easier to do that using
the ``History`` button).
Alternately, and more importantly, you can copy that URL and provide it to other developers.
They can start up the application on their workstations and see exactly what you see, a real boon for
describing problems.

This apporoach works even better when you keep a GraphQL server running on a shared staging server.
On split teams, the developers creating the application can easily explore the interface exposed
by the GraphQL server, without writing a line of code.

Trust me, they love that.

The ``< Docs`` button on the right opens the documentation browser:

.. image:: /_static/tutorial/graphiql-doc-browser.png

The documentation browser is invaluable: it allows you to navigate around your schema, drilling down
to queries, objects, and fields to see a summary of their
declaration, as well as their documentation - those
the ``:documentation`` values we added way back
:doc:`init-schema <at the beginning>`.

Take some time to learn what GraphiQL can do for you.

