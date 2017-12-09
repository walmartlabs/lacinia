Adding Members and Ratings
==========================

We're now starting an arc towards adding our first mutations.

We're going to extend or schema to add Members (the name for a user of the Clojure Game Geek web site),
and GameRatings ... how a member has rated a game, on a scale of one to five.

Each Member can rate any BoardGame, but can only rate the game once.

Schema Changes
--------------

First, let's add new fields, types, and queries to support these new features:

.. ex:: member-ratings resources/cgg-schema.edn
   :emphasize-lines: 7-8,23-29,37-48,69-

For a particular BoardGame, you can get just a simple summary of the ratings: the total number,
and a simple average.

We've added a new top-level entity, Member.
From a Member, you can get a detailed list of all the game's that member has rated.

Data Changes
------------

We'll model these ratings in our test data, much as we would a many-to-many relationship within
a SQL database:


.. ex:: member-ratings dev-resources/cgg-data.edn
   :emphasize-lines: 26-40

New Resolvers
-------------

Our schema changes introduced a few new field resolvers, which we must implement:

.. ex:: member-ratings src/clojure_game_geek/schema.clj
  :emphasize-lines: 10-13,34-58,69,71,72,74

We've generalized ``resolve-game-by-id`` into ``resolve-element-by-id``.

We've introduced three new resolvers, ``rating-summary``, ``member-ratings``, and ``game-rating->game``.

These new resolvers are implemented using a new pattern.
The existing resolvers, such as ``resolve-designer-games``, took an initial parameter
(a slice of the in-memory database), plus
the standard triumvirate of context, field arguments, and containing value.
This approach is concise, but requires the use of ``partial`` (to supply that initial parameter)
when building the resolvers map.

These new resolvers use a factory pattern instead: the extra value (the database map) is the only
parameter, which is captured in a closure; a stand-alone field resolver function, one
that accepts exactly the standard triumvirate, is returned.
No use of ``partial`` needed anymore.

Further, this new pattern is closer to what we'll end up with in a later tutorial chapter, when we see
how to use a Component as a field resolver.

Testing it Out
--------------

Back at the REPL, we can test out the new functionality.
First, select the rating summary data for a game::

   (q "{ game_by_id(id: \"1237\") { name rating_summary { count average }}}")
   =>
   {:data {:game_by_id {:name "7 Wonders: Duel",
           :rating_summary {:count 3,
                            :average 4.333333333333333}}}}


We can also lookup a member, and find all the games they've rated::

    (q "{ member_by_id(id: \"1410\") { member_name ratings { game { name } rating }}}")
    =>
    {:data {:member_by_id {:member_name "bleedingedge",
                           :ratings [{:game {:name "Zertz"}, :rating 5}
                                     {:game {:name "Tiny Epic Galaxies"}, :rating 4}
                                     {:game {:name "7 Wonders: Duel"}, :rating 4}]}}}

