Game Rating Mutation
====================

We're finally ready to add our first mutation, which will be used to add
a GameRating.

Our goal is a mutation which allows a member of ClojureGameGeek to apply a rating to a game.
We must cover two cases: one where the member is adding an entirely new
rating, and one where the member is revising a prior rating.

Along the way, we'll also start to see how to handle errors, which
tend to be more common when implementing mutations than with queries.

It is implicit that queries are idempotent (can be repeated getting the same results,
and don't change server-side state), whereas mutations
are expected to make changes to server-side state as a side-effect.
However, that side-effect is essentially invisible to Lacinia, as
it will occur during the execution of a field resolver function.

The difference between a query and a mutation in GraphQL is razor thin.
When the incoming query document contains only a single top level
operation, as is the case in all the examples so far in this tutorial,
then there is no difference at all between them.

When the query document contains multiple mutations, then the top-level mutations
execute sequentially; the first completes before the second begins execution.
For queries, execution order is not in a specified order (though the order of keys and values
is specified by the client query).

We'll consider the changes here back-to-front, starting with our database
(which is still just a map inside an Atom).

Database Layer Changes
----------------------

.. literalinclude:: /_examples/tutorial/db-2.clj
   :caption: src/clojure_game_geek/db.clj
   :emphasize-lines: 70-

.. sidebar:: What's an upsert?

  Simply put, an upsert will be either an insert (if the row is new)
  or an update (if the row exists). This terminology is used in
  the Cassandra database; in some SQL dialects it is called a merge.

Now, our goal here is not efficiency, it's to provide clear and concise code.
Efficiency comes later.

To that goal, the meat of the upsert, the ``apply-game-rating`` function,
simply removes any prior row, and then adds a new
row with the provided rating value.

Schema Changes
--------------

Our only change to the schema is to introduce the new mutation.

.. literalinclude:: /_examples/tutorial/cgg-schema-5.edn
   :caption: resources/cgg-schema.edn
   :emphasize-lines: 71-

Mutations nearly always include field arguments to define what
will be affected by the mutation, and how.
Here we have to provide field arguments to identify the game, the member,
and the new rating.

Just as with queries, it is necessary to define what value will be
resolved by the mutation; typically, when a mutation modifies a single
object, that object is resolved.

Here, resolving a GameRating didn't seem to provide value, and
we arbitrarily decided to instead resolve the BoardGame ... we could have just as easily
resolved the Member instead.
The right option is often revealed based on client requirements.

GraphQL doesn't have a way to describe error cases comparable to how
it defines types: *every* field resolver may return errors instead of,
or in addition to, an actual value.
We attempt to document the kinds of errors that may occur as part of
the operation's documentation.

Code Changes
------------

Finally, we knit together the schema changes and the database changes
in the ``schema`` namespace.

.. literalinclude:: /_examples/tutorial/schema-6.clj
   :caption: src/clojure_game_geek/schema.clj
   :emphasize-lines: 22-46,84,7

It all comes together in the ``rate-game`` function;
we first check that the ``gameId`` and ``memberId`` passed in
are valid (that is, they map to actual BoardGames and Members).

The ``resolve-as`` function is essential here: the first parameter is the
value to resolve and is often nil when there are errors.
The second parameter is an error map. [#errormaps]_

``resolve-as`` returns a wrapper object around the resolved value
(which is nil in these examples) and the error map.
Lacinia will later pull out the error map, add additional details,
and add it to the ``:errors`` key of the result map.

These examples also show the use of the ``:status`` key in the
error map.
lacinia-pedestal will look for such values in the result map, and
will set the HTTP status of the response to any value it finds
(if there's more than one, the HTTP status will be the maximum).
The ``:status`` keys are stripped out of the error maps **before**
the response is sent to the client. [#spec]_

At the REPL
-----------

Let's start by seeing the initial state of things, using the default database::

    (q "{ memberById(id: \"1410\") { name ratings { game { id name } rating }}}")
    =>
    {:data {:memberById {:name "bleedingedge",
                         :ratings [{:game {:id "1234", :name "Zertz"}, :rating 5}
                                   {:game {:id "1236", :name "Tiny Epic Galaxies"}, :rating 4}
                                   {:game {:id "1237", :name "7 Wonders: Duel"}, :rating 4}]}}}

Ok, so maybe we've soured on Tiny Epic Galaxies for the moment::

    (q "mutation { rateGame(memberId: \"1410\", gameId: \"1236\", rating: 3) { ratingSummary { count average }}}")

    => {:data {:rateGame {:ratingSummary {:count 1, :average 3.0}}}}
    (q "{ memberById(id: \"1410\") { name ratings { game { id name } rating }}}")

    =>
    {:data {:memberById {:name "bleedingedge",
                         :ratings [{:game {:id "1236", :name "Tiny Epic Galaxies"}, :rating 3}
                                   {:game {:id "1234", :name "Zertz"}, :rating 5}
                                   {:game {:id "1237", :name "7 Wonders: Duel"}, :rating 4}]}}}

Dominion is a personal favorite, so let's rate that::

    (q "mutation { rateGame(memberId: \"1410\", gameId: \"1235\", rating: 4) { name ratingSummary { count average }}}")
    => {:data {:rateGame {:name "Dominion", :ratingSummary {:count 1, :average 4.0}}}}

We can also see what happens when the query contains mistakes::

    (q "mutation { rateGame(memberId: \"1410\", gameId: \"9999\", rating: 4) { name ratingSummary { count average }}}")

    =>
    {:data {:rateGame nil},
     :errors [{:message "Game not found",
               :locations [{:line 1, :column 12}],
               :path [:rateGame],
               :extensions {:status 404, :arguments {:memberId "1410", :gameId "9999", :rating 4}}}]}

Although the ``rate-game`` field resolver just returned a simple map (with keys ``:message`` and ``:status``),
Lacinia has enhanced the map identifying the location (within the query document), the query path
(which indicates which operation or nested field was involved), and the arguments passed to
the field resolver function.  It has also moved any keys it doesn't recognize, in this case ``:status`` and ``:arguments``, to an embedded ``:extensions`` map.

In Lacinia, there's a difference between a resolver error, from using ``resolve-as``, and an overall failure parsing
or executing the query.
If the ``rating`` argument is omitted from the query, we can see a significant difference::

    (q "mutation { rateGame(memberId: \"1410\", gameId: \"9999\") { name ratingSummary { count average }}}")

    =>
    {:errors [{:message "Exception applying arguments to field `rateGame': Not all non-nullable arguments have supplied values.",
               :locations [{:line 1, :column 12}],
               :extensions {:field-name :Mutation/rateGame, :missing-arguments [:rating]}}]}

Here, the result map contains *only* the ``:errors`` key; the ``:data`` key is missing.
A similar error would occur if the type of value provided to field argument is unacceptible::

    (q "mutation { rateGame(memberId: \"1410\", gameId: \"9999\", rating: \"Great!\") { name rating_summary { count average }}}")

    =>
    {:errors [{:message "Exception applying arguments to field `rateGame': For argument `rating', unable to convert \"Great!\" to scalar type `Int'.",
               :locations [{:line 1, :column 12}],
               :extensions {:field-name :Mutation/rateGame,
                            :argument :Mutation/rateGame.rating,
                            :value "Great!",
                            :type-name :Int}}]}


Summary
-------

And now we have mutations! The basic structure of our application is nearly fully formed, but we can't go to production with an in-memory database. In the next chapter, we'll start work on storing the database data in an actual SQL database.

.. [#errormaps] Each map must contain, at a minimum, a ``:message`` key.
.. [#spec] The very idea of changing the HTTP response status is somewhat antithetical
   to some GraphQL developers and this behavior is optional, but on by default.
