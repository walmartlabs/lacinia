Domain
======

Our goal will be to provide a GraphQL interface to data about board games
(one of the author's hobbies), as a limited version of
`Board Game Geek <https://boardgamegeek.com/>`_.

The basic types are as follows:

.. graphviz::

   digraph {

    BoardGame
    Publisher
    Designer
    AppUser
    Review

    BoardGame -> Review [taillabel="1", headlabel="n"]
    BoardGame -> {Publisher, Designer} [taillabel="n", headlabel="m"]
    Review -> AppUser [taillabel="n", headlabel="1" ]

   }

A BoardGame may be published by multiple Publisher companies (the Publisher may
be different for different countries, or may simply vary over time).

A BoardGame may have any number of Designers.

Users, represented as AppUser, may leave reviews for board games.
