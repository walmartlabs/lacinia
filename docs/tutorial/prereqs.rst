Pre-Requisites
==============

You should have a basic understanding of GraphQL, which you can pick up from this documentation,
and from `the GraphQL home page <https://graphql.org/>`_.

You should be familiar with, but by no means an expert in, Clojure.

You should have a `recent build of Clojure <https://clojure.org/guides/install_clojure>`_, including the ``clj`` command.

You should have an editor or IDE ready to go, set up for editing Clojure code.

A skim of the Lacinia reference documentation (the rest of this manual, outside of
this tutorial) is also helpful, or you can follow links provided as we go.

The later chapters use a database stored in a Docker container [#docker]_;
you should download and install `Docker <https://www.docker.com/>`_ and
ensure that you can run the ``docker`` command.

.. [#docker] A Docker container is
   the  `Inception <http://www.imdb.com/title/tt1375666/>`_ of computers; a
   container is essentially a
   light-weight virtual machine that runs inside your computer.

   To the `PostgreSQL <https://www.postgresql.org/>`_ server we'll be running inside the container, it will appear as if
   the entire computer is running Linux, just as if Linux and PostgreSQL were installed
   on a bare-metal computer.

   Docker images
   are smaller and less demanding than full operating system virtual machines. In fact
   frequently you will run several interconnected containers together.

   Docker includes infrastructure for downloading the images from a central repository.
   Ultimately, it's faster and easier to get PostgreSQL running
   inside a container that to install the database onto your computer.
