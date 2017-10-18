Tutorial
========

.. caution::

   This tutorial is a work-in-progress ... and work has only *just* started.
   It will likely be weeks or months of part-time work.  PR's welcome!

Our goal with this tutorial is to build up the essentials of a full application
implemented in Lacinia, starting from nothing.

Unlike many of the snippets used elsewhere in the Lacinia documentation, this will be something
you can fork and experinment with your self.

Along the way, we hope you'll learn quite a bit about not just Lacinia and GraphQL,
but about building Clojure applications in general.

Pre-Requisites
--------------

You should be familiar with, but by no means an expert, in Clojure.

You should have `Leiningen <github.com/technomancy/leiningen>`_, the Clojure build tool, installed and be familiar with
editting a `project.clj` file.

You should have a editor or IDE ready to go for Clojure.

Domain
------

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

    BoardGame -> {Designer, Review, Publisher} [taillabel="1", headlabel="n"]
    Review -> AppUser [taillabel="n", headlabel="1" ]

   }

A BoardGame may be published by multiple Publisher companies (the Publisher may
be different for different countries, or may simply vary over time).

A BoardGame may have any number of Designers.

Users may leave reviews for board games.
