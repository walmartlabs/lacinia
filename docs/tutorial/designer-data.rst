Adding Designers
================

So far, we've been working with just a single entity type, BoardGame.

Let's see what we can do when we add Designer to the mix.

Initially, we'll define each designer in terms of an id, a name, and an optional
home page URL.

.. ex:: add-designers dev-resources/cgg-data.edn
   :emphasize-lines: 7,11,16,22,26-

If this was a relational database, we'd likely have a join table between
BoardGame and Designer, but that can come later.
For now, we have a set of designer `ids` inside each BoardGame.

Schema Changes
--------------

.. ex:: add-designers resources/cgg-schema.edn
   :emphasize-lines: 11-13,21-30

We've added a ``:designers`` field to BoardGame, and added
a new Designer type.

Notice that we've defined the ``:designers`` field as ``(non-null (list :Designer))``.
This is somewhat overkill (the world won't end if the result map contains a nil instead of an
empty list), but demonstrates that the ``list`` and ``non-null`` modifiers can
nest properly.

We could go further: ``(non-null (list (non-null :Designer)))`` ... but that's
not really adding value.

We need a field resolver for the ``:designers`` field, to convert from
what's in our data (a set of designer ids) into what we are promising in the schema:
a list of Designer objects.

Likewise, we need a field resolver in the Designer entity to figure out which BoardGames
are associated with the designer.

Code Changes
------------

.. ex:: add-designers src/clojure_game_geek/schema.clj
   :emphasize-lines: 14-31, 38-39, 41-42

As with all field resolvers [#root]_, ``resolve-board-game-designers`` is passed the containing resolved value
(a BoardGame, in this case)
and in turn, resolves the next step down, in this case, a list of Designers.

This is an important point: the data from your external source does not have to be in the shape
described by your schema ... you just must be able to transform it into that shape.
Field resolvers come into play both when you need to fetch data from an external source,
and when you need to reshape that data to match the schema.

GraphQL doesn't make any guarantees about order of values in a list field;
when it matters, it falls on us to add documentation to describe the order,
or even provide field arguments to let the client specify the order.

The inverse of ``resolve-board-game-designers`` is ``resolve-designer-games``.
It starts with a Designer and uses the Designer's id as a filter to find
BoardGames whose ``:designers`` set contains the id.

Testing It Out
--------------

After reloading code in the REPL, we can exercise these new types and relationships::

  (q "{ game_by_id(id: \"1237\") { name designers { name }}}")
  => {:data {:game_by_id {:name "7 Wonders: Duel",
                          :designers [{:name "Antoine Bauza"}
                                      {:name "Bruno Cathala"}]}}}

For the first time, we're seeing the "graph" in GraphQL.

An important part of GraphQL is that your query must always extend to scalar fields;
if you stop at a compound type, Lacinia will report an error instead::

  (q "{ game_by_id(id: \"1237\") { name designers }}")
  =>
  {:errors [{:message "Field `designers' (of type `Designer') must have at least one selection.",
             :locations [{:line 1, :column 25}]}]}


Notice how the ``:data`` key is not present here ... that indicates that the error
occured during the parse and prepare phases, before execution in earnest began.

To really demonstrate navigation, we can go from BoardGame to Designer and back::

  (q "{ game_by_id(id: \"1234\") { name designers { name games { name }}}}")
  => {:data {:game_by_id {:name "Zertz",
                          :designers [{:name "Kris Burm",
                                       :games [{:name "Zertz"}]}]}}}

.. [#root] Root resolvers, such as for the ``game_by_id`` query operation, are the
   exception: they are passed nil.
