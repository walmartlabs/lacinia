Refactoring to Components
=========================

Before we add the next bit of functionality to our application, it's time to
take a small detour, into the use of Sandra Sierra's
`Component <https://github.com/stuartsierra/component>`_ library. [#vid]_

As Clojure programs grow, the namespaces, and relationships between those
namespaces, grow in number and complexity.
In our :doc:`previous example <pedestal>`, we saw that the logic to
start the Jetty instance was strewn across the ``user`` namespace.

This isn't a problem in our toy application, but as a real application grows, we'd
start to see some issues and concerns:

* A single 'startup' namespace (maybe with a ``-main`` method) imports every
  single other namespace.
* Potential for duplication or conflict between the `real` startup code and the
  `test` startup code. [#test]_
* Is there a good way to `stop` things, say, between tests?
* Is there a way to mock parts of the system (for testing purposes)?
* We really want to avoid a proliferation of global variables. Ideally, none!

Component is a simple, no-nonsense way to achieve the above goals.
It gives you a clear way to organize your code, and it does things in a fully
`functional` way: no globals, no update-in-place, and easy to reason about.

The building-block of Component is, unsurprisingly, components.
These components are simply ordinary Clojure maps -- though for reasons we'll discuss
shortly, Clojure record types are more typically used.

The components are formed into a system, which again is just a map.
Each component has a unique, well-known key in the system map.

Components `may` have dependencies on other components.
That's where the fun really starts.

Components `may` have a lifecycle; if they do, they implement the Lifecycle
protocol containing methods ``start`` and ``stop``.
This is why many components are implemented as Clojure records ...
records can implement a protocol, but simple maps can't.

Rather than get into the minutiae, let's see how it all fits together in
our Clojure Game Geek application.

Add Dependencies
----------------

.. literalinclude:: /_examples/tutorial/deps-5.edn
   :caption: deps.edn
   :emphasize-lines: 5

We've added the ``component`` library.

System Map
----------

We're starting quite small, with just two components in our system:

.. graphviz::

    digraph {

      server [label=":server"]
      schema [label=":schema-provider"]

      server -> schema

    }

The ``:server`` component is responsible for setting up the Pedestal service,
which requires a compiled Lacinia schema.
The ``:schema-provider`` component exposes that schema as its ``:schema`` key.

Later, we'll be adding additional components for other logic, such as database connections,
thread pools, authentication/authorization checks, caching, and so forth.
But it's easier to start small.

What does it mean for one service to depend on another?
Dependencies are acted upon when the system is started (and again when
the system is stopped).

The dependencies influence the order in which each component is started.
Here, ``:schema-provider`` is started before ``:server``, as ``:server`` depends on
``:schema-provider``.

Secondly, the *started* version of a dependency is ``assoc``-ed into
the dependant component.
After ``:schema-provider`` starts, the started version of the component
will be ``assoc``-ed as the ``:schema-provider`` key of the ``:server`` component.

Once a component has its dependencies ``assoc``-ed in, and is itself started
(more on that in a moment), it may be ``assoc``-ed into further components.

The Component library embraces the identity vs. state concept; the identity of
the component is its key in the system map ... its state is a series of transformations
of the initial map.

:schema-provider component
--------------------------

The ``clojure-game-geek.schema`` namespace has been extended to provide
the ``:schema-provider`` component.

.. literalinclude:: /_examples/tutorial/schema-3.clj
   :caption: src/my/clojure_game_geek/schema.clj
   :emphasize-lines: 4,34,45,49,52-64

The significant changes are at the bottom of the namespace.
There's a new record, SchemaProvider, that implements the Lifecycle
protocol.

Lifecycle is optional; trivial components may not need it.
In our case, we use the ``start`` method as an opportunity to
load and compile the Lacinia schema.

Notice that we are passing the component into ``load-schema``.
This isn't necessary yet, but in later iterations of the Clojure Game Geek application, the
``:schema-provider`` component will have dependencies on other components,
generally because a field resolver will need access to the component.

When you implement a protocol, you must implement all the methods of the
protocol.
In Component's Lifecycle protocol, you typically will undo in ``stop`` whatever you did in ``start``.
For example, a Component that manages a database connection will open it in ``start`` and
close it in ``stop``.

Here we just get rid of the compiled schema, [#clear]_
but it is also common
and acceptable for a ``stop`` method to just return ``this`` if the component
doesn't have external resources,
such as a database connection, to manage.

Finally, the ``new-schema-provider`` function is a constructor around the
SchemaProvider record.
It returns a single-element map, associating the ``:schema-provider`` system key for
the component with the initial iteration of the component itself. [#system]_

:server component
-----------------

Next well add the ``clojure-game-geek.server`` namespace to provide the
``:server`` component.

.. literalinclude:: /_examples/tutorial/server-1.clj
   :caption: src/my/clojure_game_geek/server.clj

Much of the code previously in the ``user`` namespace has moved here.

You can see how the components work together, inside the ``start``
method.
The Component library has ``assoc``-ed the ``:schema-provider`` component
into the ``:server`` component, so it's possible to get the ``:schema`` key
and build the Pedestal server from it.

``start`` and ``stop`` methods often have side-effects.
This is explicit here, with the call to ``http/stop`` before clearing
the ``:server`` key.

The ``new-server`` function not only gives the component its system key
and initial state, but also invokes ``component/using`` to establish
the dependency on the ``:schema-provider`` component.

system namespace
----------------

We'll create a new ``my.clojure-game-geek.system`` namespace just to put together the Component system map.

.. literalinclude:: /_examples/tutorial/system-1.clj
   :caption: src/my/clojure_game_geek/system.clj

You can imagine that, as the system grows larger, so will this namespace.
But at the same time, the namespaces for the individual components will only
need to know about the namespaces of components they directly depend upon.

user namespace
--------------

Next, we'll look at changes to the ``user`` namespace:

.. literalinclude:: /_examples/tutorial/user-4.clj
  :caption: dev-resources/user.clj
  :emphasize-lines: 5, 7, 27, 31-34, 37-

The ``user`` namespace has shrunk; previously
it was responsible for loading the schema, and creating and starting
the Pedestal service; this has all shifted to the individual components.

Instead, the user namespace creates a system map, and can use
``start-system`` and ``stop-system`` on that system map: no direct knowledge of
loading schemas or starting and stopping Pedestal is present any longer.

The user namespace previously had vars for both the schema and the Pedestal
service.
Now it only has a single var, for the Component system map.

Interestingly, as our system grows later, the user namespace will likely
not change at all, just the system map it gets from ``system/new-system`` will
expand.

The only wrinkle here is in the ``q`` function; since there's no longer a local
``schema`` var it is necessary to pull the ``:schema-provider`` component from the system map,
and extract the schema from that component.

Summary
-------

Even with just two components, using the Component library simplifies our code,
and lays the groundwork for rapidly expanding the behaviour of the application.

In the next chapter, we'll look at adding new queries and types to the schema,
in preparation to adding our first mutations.


.. [#vid] Sandra provides a really good explanation of Component in their
   `Clojure/West 2014 talk <https://www.youtube.com/watch?v=13cmHf_kt-Q&t=1106s>`_.
.. [#test] We've been sloppy so far, in that we haven't even thought about
   testing. That will change shortly.
.. [#clear] You might be tempted to use a ``dissoc`` here, but if you
   ``dissoc`` a declared key of a record, the result is an ordinary
   map, which can break tests that rely on repeatedly starting and stopping
   the system.
.. [#system] This is just one approach; another would be to provide a function
   that ``assoc``-ed the component into the system map.
