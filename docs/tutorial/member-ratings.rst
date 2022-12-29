Adding Members and Ratings
==========================

We're now starting an arc towards adding our first mutations.

We're going to extend our schema to add Members (the name for a user of the Clojure Game Geek web site),
and GameRatings ... how a member has rated a game, on a scale of one to five.

Each Member can rate any BoardGame, but can only rate any single game once.

Schema Changes
--------------

First, let's add new fields, types, and queries to support these new features:

.. literalinclude:: /_examples/tutorial/cgg-schema-4.edn
   :caption: resources/cgg-schema.edn
   :emphasize-lines: 9-10,22-45,65-

For a particular BoardGame, you can get just a simple summary of the ratings: the total number,
and a simple average.

We've added a new top-level entity, Member.
From a Member, you can get a detailed list of all the games that member has rated.

Data Changes
------------

We'll model these ratings in our test data, much as we would a many-to-many relationship within
a SQL database:


.. literalinclude:: /_examples/tutorial/cgg-data-3.edn
   :caption: dev-resources/cgg-data.edn
   :emphasize-lines: 26-40

New Resolvers
-------------

Our schema changes introduced a few new field resolvers, which we must implement:

.. literalinclude:: /_examples/tutorial/schema-4.clj
  :caption: src/clojure_game_geek/schema.clj
  :emphasize-lines: 9-13,34-57,66-69,71-74

We've generalized ``resolve-game-by-id`` into ``resolve-element-by-id`` so that we could
re-use the logic for the ``memberById`` query.  This is another example
of a higher order function, in that it is a function that is passed in a map
and returns a new function that closes [#closes]_ on the provided element map (a map of BoardGames in
one case, a map of Members in the other).

We've introduced three new resolvers, ``rating-summary``, ``member-ratings``, and ``game-rating->game``.

These new resolvers are implemented using a the same style as
``resolve-element-by-id``; each function acts as a factory, returning the actual
field resolver function. No use of ``partial`` is needed anymore.

This new pattern is closer to what we'll end up with in a later tutorial chapter, when we see
how to use a Component as a field resolver.

It's worth emphasising again that field resolvers don't just access data, they can transform it.
The ``ratingSummary`` field resolver is an example of that; there's no database entity directly
corresponding to the schema type ``:GameRatingSummary``, but the field resolver can build that information directly.
There doesn't even have to be a special type or record ... just a standard Clojure map
with the correctly named keys.

Testing it Out
--------------

Back at the REPL, we can test out the new functionality.
We need the server started after the component refactoring::

   (start)
   => :started

First, select the rating summary data for a game::

    (q "{ gameById(id: \"1237\") { name ratingSummary { count average }}}")
    => {:data {:gameById {:name "7 Wonders: Duel", :ratingSummary {:count 3, :average 4.333333333333333}}}}


We can also lookup a member, and find all the games they've rated::

    (q "{ memberById(id: \"1410\") { name ratings { game { name } rating }}}")
    =>
    {:data {:memberById {:name "bleedingedge",
                         :ratings [{:game {:name "Zertz"}, :rating 5}
                                   {:game {:name "Tiny Epic Galaxies"}, :rating 4}
                                   {:game {:name "7 Wonders: Duel"}, :rating 4}]}}}

In fact, leveraging the "graph" in GraphQL, we can compare a member's ratings to the averages::

    (q "{ memberById(id: \"1410\") { name ratings { game { name  ratingSummary { average }}  rating }}}")
    =>
    {:data {:memberById {:name "bleedingedge",
                         :ratings [{:game {:name "Zertz", :ratingSummary {:average 4.0}}, :rating 5}
                                   {:game {:name "Tiny Epic Galaxies", :ratingSummary {:average 4.0}}, :rating 4}
                                   {:game {:name "7 Wonders: Duel", :ratingSummary {:average 4.333333333333333}}, :rating 4}]}}}

Summary
-------

We're beginning to pick up the pace, working with our application's simple skeleton
to add new types and relationships to the queries.

Next up, we'll add new components to manage an in-memory database.


.. [#closes] This is a computer science term that means that the value, ``element-map``,
   will be in-scope inside the returned function after the ``resolve-element-by-id``
   function returns; the returned function is a `closure` and, yes, that's part of
   the basis for the name Clojure.
