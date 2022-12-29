Mutable Database
================

We're still not quite ready to implement our mutation ... because we're storing our data in an
immutable map.
Once again, we're not going to take on running an external database, instead we'll put our
immutable map inside an Atom.
We'll also do some refactoring that will make our eventual transition to an external database
that much easier.

System Map
----------

In the previous versions of the application, the database data was an immutable map, and all the logic
for traversing that map was inside the ``clojure-game-geek.schema`` namespace.
With this change, we're breaking things apart, there'll be a new namespace, and new component,
to encapsulate the database itself.

.. graphviz::

    digraph {

      server [label=":server"]
      schema [label=":schema-provider"]
      db [label=":db"]

      server -> schema -> db

    }


db namespace
------------

.. literalinclude:: /_examples/tutorial/db-1.clj
   :caption: src/clojure_game_geek/db.clj

This namespace does two things:

* Defines a component in terms of a record and a constructor function

* Provides an API for database access focused upon that component

At this point, the Component is nothing more than a home for the ``:data`` Atom.
That Atom is created and initialized inside the ``start`` lifecycle method.

All of those data access functions follow.

This code employs a few reasonable conventions:

* ``find-`` prefix for functions that get data by primary key, and may return nil if not found

* ``list-`` prefix is like ``find-``, but returns a seq of matches

* The ``:db`` component is always the first parameter, as ``db``

Later, when we add some mutations, we'll define further functions and new naming and coding conventions.

The common trait for all of these is the ``(-> db :data deref ...)`` code; in other words,
reach into the component, access the ``:data`` property (the Atom) and deref the Atom to get the
immutable map.

Looking forward to when we do have an external database ... these functions will change, but
their signatures will not.
Any code that invokes these functions, for example the field resolver functions defined in
``clojure-game-geek.schema``, will work, unchanged, after we swap in the external database
implementation.

system namespace
----------------

We need to introduce the new ``:db`` component, and wire it into the system.

.. literalinclude:: /_examples/tutorial/system-2.clj
   :caption: src/clojure_game_geek/system.clj
   :emphasize-lines: 5, 10, 13-15

As promised previously, namespaces that use the system (such as the ``user`` namespace)
don't change at all.  Likewise, the ``:server`` component (and ``my.clojure-game-geek.server`` namespace
don't have to change even though the schema used by the component has changed drastically.

schema namespace
----------------

The schema namespace has shrunk, and improved:

.. literalinclude:: /_examples/tutorial/schema-5.clj
   :caption: src/clojure_game_geek/schema.clj
   :emphasize-lines: 7,15-60,63,67,75

Now all of the resolver functions are following the factory style, but they're largely just wrappers
around the functions from the ``my.clojure-game-geek.db`` namespace.

And we still don't have any tests (the shame!), but we can exercise a lot of the system from the REPL::

    (q "{ memberById(id: \"1410\") { name ratings { game { name ratingSummary { count average } designers { name  games { name }}} rating }}}")
    =>
    {:data {:memberById {:name "bleedingedge",
                         :ratings [{:game {:name "Zertz",
                                           :ratingSummary {:count 2, :average 4.0},
                                           :designers [{:name "Kris Burm", :games [{:name "Zertz"}]}]},
                                    :rating 5}
                                   {:game {:name "Tiny Epic Galaxies",
                                           :ratingSummary {:count 1, :average 4.0},
                                           :designers [{:name "Scott Almes", :games [{:name "Tiny Epic Galaxies"}]}]},
                                    :rating 4}
                                   {:game {:name "7 Wonders: Duel",
                                           :ratingSummary {:count 3, :average 4.333333333333333},
                                           :designers [{:name "Antoine Bauza", :games [{:name "7 Wonders: Duel"}]}
                                                       {:name "Bruno Cathala", :games [{:name "7 Wonders: Duel"}]}]},
                                    :rating 4}]}}}

Summary
-------

Adding a new component to manage mutable (but still in-memory) data is very straight-forward, and we've
added a new API that will be stable when we start to use an external database.

With the mutable database ready to go, we can introduce our first mutation.
