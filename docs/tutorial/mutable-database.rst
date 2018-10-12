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

.. ex:: mutable-database src/clojure_game_geek/db.clj

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

.. ex:: mutable-database src/clojure_game_geek/system.clj
   :emphasize-lines: 13

The ``:db`` component doesn't effectively exist until it is part of the system map.
This change adds it in.
As promised previously, namespaces that use the system (such as the ``user`` namespace)
don't change at all.

schema namespace
----------------

The schema namespace has shrunk, and improved:

.. ex:: mutable-database src/clojure_game_geek/schema.clj

Now all of the resolver functions are following the factory style, but they're largely just wrappers
around the functions from the ``clojure-game-geek.db`` namespace.

And we still don't have any tests (the shame!), but we can exercise a lot of the system from the REPL::

    (q "{ member_by_id(id: \"1410\") { member_name ratings { game { name rating_summary { count average } designers { name  games { name }}} rating }}}")
    =>
    {:data {:member_by_id {:member_name "bleedingedge",
                           :ratings [{:game {:name "Zertz",
                                             :rating_summary {:count 2, :average 4.0},
                                             :designers [{:name "Kris Burm", :games [{:name "Zertz"}]}]},
                                      :rating 5}
                                     {:game {:name "Tiny Epic Galaxies",
                                             :rating_summary {:count 1, :average 4.0},
                                             :designers [{:name "Scott Almes", :games [{:name "Tiny Epic Galaxies"}]}]},
                                      :rating 4}
                                     {:game {:name "7 Wonders: Duel",
                                             :rating_summary {:count 3, :average 4.333333333333333},
                                             :designers [{:name "Antoine Bauza", :games [{:name "7 Wonders: Duel"}]}
                                                         {:name "Bruno Cathala", :games [{:name "7 Wonders: Duel"}]}]},
                                      :rating 4}]}}}

