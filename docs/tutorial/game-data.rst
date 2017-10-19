Placeholder Game Data
=====================

It would be nice to do some queries that actually return some data!

One option would be to fie up a database, define some tables, and load some data in.

... but that would slow us down by hours, and not teach us anything about Lacinia
and GraphQL.

Instead, we'll create an EDN file with some test data in it, and wire that up
to the schema.
We can fuss with database and all that later in the tutorial.

cgg-data.edn
------------

.. ex:: game-data dev-resources/cgg-data.edn

This file defines just a few games I've recently played.
It will take the place of an external database.
Later, we can add more data for the other entities and their relationships.

Resolver
--------

Inside our ``schema`` namespace, we need to read the data and provide a resolver
that can access it.

.. ex:: game-data src/clojure_game_geek/schema.clj
   :emphasize-lines: 9-22

The resolver itself is the ``resolve-game-by-id`` function.
It is provided with a map of games, and the standard trio of
resolver function arguments: context, args, and value.

Field resolvers are passed a map of arguments, with keyword ids.
We use a bit of destructing to extract the id [#too-much]_.
The data in the map is already in a form that matches the GraphQL schema, so it's
just a matter of ``get``-ing it out of the games map.

Inside ``resolver-map``, we read the data, then use typical Clojure data manipulation
to get it into the form that we want.

The use of `partial` explains why ``resolve-game-by-id`` takes four parameters:
the wrapper function returned by `partial` supplies that first parameter, and passes the remaining three parameters
through into ``resolve-game-by-id``.

Running Queries
---------------

We're finally almost ready to run queries ... but first, let's get rid of
that #ordered/map business.

.. ex:: game-data dev-resources/user.clj
   :emphasize-lines: 10-25,30

This ``simplify`` function finds all the ordered maps and converts them into
ordinary maps.
It also finds an lists can converts them to vectors.

With that in place, we're ready to run some queries::

   (q "{ game_by_id(id: \"anything\") { id name summary }}")
   => {:data {:game_by_id nil}}

This hasn't changed [#repl]_, except that it's standard maps, which are easier to look at.

However, we can also get real data back from our query::

   (q "{ game_by_id(id: \"1236\") { id name summary }}")
   =>
   {:data {:game_by_id {:id "1236",
                        :name "Tiny Epic Galaxies",
                        :summary "Fast dice-based sci-fi space game with a bit of chaos"}}}

Success!
Lacinia has parsed our query string and executed it against our compiled schema.
At the correct time, it dropped into our resolver function, which supplied the data
that it then slice and diced to compose the result map.

We've made our first true steps.

.. [#too-much] This is overkill for this very simple case, but its nice to demonstrate
   techniques that are likely to be used in real applications.
.. [#repl] This REPL output is a bit different than earlier examples; we've switched from
   the standard Leiningen REPL to the `Cursive REPL <https://cursive-ide.com/>`_; the latter pretty-prints
   the returned values.
