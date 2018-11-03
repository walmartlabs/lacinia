Domain
======

Our goal will be to provide a GraphQL interface to data about board games
(one of the author's hobbies), as a limited version of
`Board Game Geek <https://boardgamegeek.com/>`_.

The basic types in the final system are as follows:

.. graphviz::

   digraph {

    BoardGame
    Publisher
    Designer
    Member
    GameRating

    BoardGame -> GameRating [taillabel="1", headlabel="n"]
    BoardGame -> {Publisher, Designer} [taillabel="n", headlabel="m"]
    GameRating -> Member [taillabel="n", headlabel="1" ]

   }

A BoardGame may be published by multiple Publisher companies (the Publisher may
be different for different countries, or may simply vary over time).

A BoardGame may have any number of Designers.

Users of Clojure Game Geek, represented as type Member, may provide their personal ratings for board games.

This is a tiny sliver of the functionality of Board Game Geek, but sufficiently meaty to give us a taste for building
full applications.
