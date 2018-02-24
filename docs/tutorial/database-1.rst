External Database, Phase 1
==========================

We've gone pretty far with our application so far, but it's time to make that big leap, and convert
things over to an actual database.
We'll be running `PostgreSQL <https://www.postgresql.org/>`_ in a
Docker container. [#container]_

We're definitely going to be taking two steps backward before taking further steps forward, but the majority of the changes
will be in the ``clojure-game-geek.db`` namespace.

Lumped in with these changes, we'll also be changing the primary key of our Member, BoardGame, and Publisher
objects to be a UUID, rather than a simple ID.


Dependency Changes
------------------

.. ex:: database-1 project.clj
   :emphasize-lines: 7, 9-11

We're bringing in the very latest versions of lacinia and lacinia-pedestal (something we'll
likely do almost every chapter).

We're also pinning the version of ``core.async`` [#async]_ to it's latest version, and bringing in
another library to allow us to access the PostgreSQL database.

Database Initialization
-----------------------

We've added a number of scripts to project.

First, a file used to start PostgreSQL:

.. ex:: database-1 docker-compose.yml

This file is used with the ``docker-compose`` command to set up one or more containers.
We only define a single container right now.
The ``image:`` identifies the name of the image, which is hosted at `hub.docker.com <http://hub.docker.com>`_.
The port mapping is part of the magic of Docker ... the PostgreSQL server, inside the container,
will listen to requests on its normal port: 5432, but our code, running on the host operation system,
can reach the server as port 25432 on ``localhost``.

The ``docker-up.sh`` script is used to start the container:

.. ex:: database-1 bin/docker-up.sh

There's also a ``docker-down.sh`` script to shut down the container:

.. ex:: database-1 bin/docker-down.sh

Finally, after starting the container, we need to setup the database and initial data.

.. ex:: database-1 bin/setup-db.sh

.. sidebar:: Setup

   `Install Docker <https://www.docker.com/docker-mac>`_ first,
   then make sure you execute ``bin/docker-up.sh``, and finally ``bin/setup-db.sh`` before running the
   revised application.

This creates a ``cggdb`` database and a ``cgg_role`` user.
It also creates a basic set of tables and puts a small amount of initial data in.

UUIDs
-----

.. [#container] A `Docker <https://www.docker.com/>`_ container is
   the  `Inception <http://www.imdb.com/title/tt1375666/>`_ of computers; a
   container is essentially a
   light-weight virtual machine that runs inside your computer. Docker images
   are smaller and less demanding than full operating system virtual machines. In fact
   frequently you will run several interconnected containers together.

   There's infrastructure for downloading the images from a central repository.
   Ultimately, it's faster and easier to get it running
   inside a container that to install it onto your computer.

.. [#async] core.async is a very powerful library for performing asynchronous computation
   in Clojure. We'll discuss core.async, and how it relates to Lacinia, in a later chapter.
