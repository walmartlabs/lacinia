Placeholder Game Data
=====================

It would be nice to do some queries that actually return some data!

One option would be to fire up a database, define some tables, and load some data in.

... but that would slow us down, and not teach us anything about Lacinia
and GraphQL.

Instead, we'll create an EDN file with some test data in it, and wire that up
to the schema.
We can fuss with database access and all that later in the tutorial.

cgg-data.edn
------------

.. literalinclude:: /_examples/tutorial/cgg-data-1.edn
   :caption: dev-resources/cgg-data.edn

This file defines just a few games I've recently played.
It will take the place of an external database.
Later, we can add more data for the other entities and their relationships.

Although the keys here are camel-case (like Java or JavaScript) and not kebab-case (like Clojure and
other Lisps), they are still valid Clojure keywords and, more importantly, they match the field names in the GraphQL schema.
Lacinia doesn't do any trickery here, field names in the schema are matched directly to corresponding
keyword keys in the value maps.

Later in this tutorial, we'll actually connect our application up to an external database.

Resolver
--------

Inside our ``schema`` namespace, we need to read the data and provide a resolver
that can access it.

.. literalinclude:: /_examples/tutorial/schema-1.clj
   :caption: src/my/clojure_game_geek/schema.clj
   :emphasize-lines: 8-21


You can see a bit of the philosophy of Lacinia inside the ``load-schema`` function: Lacinia strives
to provide only what is most essential, or truly useful and universal.

Lacinia explicitly `does not` provide a single function to read, parse, inject resolvers, and compile an EDN file in a single call.
That may seem odd -- it feels like every application will just cut-and-paste something virtually identical to ``load-schema``.

In fact, not all schemas will come directly from a single EDN file.
Because the schema is Clojure `data` it can be constructed, modified, merged, and otherwise transformed
right up to the point that it is compiled.
By starting with a pipeline like the one inside ``load-schema``, it becomes easy to inject your own application-specific bits
into the steps leading up to ``schema/compile``, which ultimately becomes quite essential.

Back to the schema; the resolver itself is the ``resolve-game-by-id`` function.
It is provided with a map of games, and the
:doc:`standard triumvirate of
resolver function arguments<../resolve/overview>`: context, field arguments, and container value.

Field resolvers are passed a map of the field arguments (from the client query).
This map contains keyword keys, and values of varying types (because field arguments have a type in
the GraphQL schema).

We use a bit of :clojure:`destructuring <reference/special_forms#_map_binding_destructuring>` to extract the id [#too-much]_.
The data in the map is already in a form that matches the GraphQL schema, so it's
just a matter of ``get``-ing it out of the games map.

Inside ``resolver-map``, we read the sample game data, then use typical Clojure data manipulation
to get it into the form that we want: we convert a seq of BoardGame maps into a map of maps, keyed on the ``:id`` of each
BoardGame.

The ``partial`` function is a real workhorse in Clojure code; it takes an existing function and a set of initial arguments
to that function and returns a new function that collects the remaining arguments needed by the original function.
This returned function will accept the standard field resolver arguments -- ``context``, ``args``, and ``value``,
and pass the ``games-map`` and those three arguments to ``resolve-game-by-id``.

This is one common example of the use of `higher orderered functions`.
It's not as complicated as the term might lead you to believe - just that functions can be arguments to, and return
values from, other functions.

Running Queries
---------------

We're finally almost ready to run queries ... but first, let's get rid of
that ``#ordered/map`` business.

.. literalinclude:: /_examples/tutorial/user-2.clj
   :caption: dev-resources/user.clj
   :emphasize-lines: 4-24,28-30

This ``simplify`` function finds all the ordered maps and converts them into
ordinary maps.
It also finds any lists and converts them to vectors.

With that in place, we're ready to reload our code [#reload]_, and then run some queries::

   (q "{ gameById(id: \"anything\") { id name summary }}")
   => {:data {:gameById nil}}

This hasn't changed [#repl]_, except that, because of ``simplify``, the final result is just standard maps,
which are easier to look at in the REPL.

However, we can also get real data back from our query::

   (q "{ gameById(id: \"1236\") { id name summary minPlayers }}")

   =>
   {:data {:gameById {:id "1236",
                      :name "Tiny Epic Galaxies",
                      :summary "Fast dice-based sci-fi space game with a bit of chaos",
                      :minPlayers 1}}}

.. sidebar:: Where's the JSON?

   It's perfectly acceptable to return EDN rather than JSON.
   The GraphQL specification goes to some length to identify JSON as just one
   possible over-the-wire format.
   It's easy enough to convert EDN to JSON, and even reasonable to
   support clients that can consume the EDN directly.

Success!
Lacinia has parsed our query string and executed it against our compiled schema.
At the correct time, it dropped into our resolver function, which supplied the data
that Lacinia then sliced and diced to compose the result map.

You should be able to devise and execute other simple queries at this point.


Summary
-------

We've extended our schema and field resolvers with test data and are getting
some actual data back when we execute a query.

Next up, we'll continue extending the schema, and start discussing relationships between GraphQL types.


.. [#too-much] This is overkill for this very simple case, but it's nice to demonstrate
   techniques that are likely to be used in real applications.

.. [#reload] How to reload your code is going to be specific to your IDE;
   `Cursive <https://cursive-ide.com/>`_ adds a ``Load File in REPL`` action that loads the current file and any changed namespaces in dependency order automatically.

   If you are new
   to Clojure or not using Cursive, this is a big area to dive into; you can start
   with the `Programming at the REPL guide <https://clojure.org/guides/repl/enhancing_your_repl_workflow>`_.

.. [#repl] This REPL output is a bit different than earlier examples; we've switched from
   the standard ``clj`` REPL to the `Cursive REPL <https://cursive-ide.com/>`_; the latter pretty-prints
   the returned values.
