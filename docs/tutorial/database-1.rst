External Database, Phase 1
==========================

We've gone pretty far with our application so far, but it's time to make that big leap, and convert
things over to an actual database.
We'll be running `PostgreSQL <https://www.postgresql.org/>`_ in a
Docker container. [#container]_

We're definitely going to be taking two steps backward before taking further steps forward, but the majority of the changes
will be in the ``clojure-game-geek.db`` namespace; the majority of the application, including the
field resolvers, will be unaffected.

Dependency Changes
------------------

.. ex:: database-1e project.clj
   :emphasize-lines: 7, 9-11

We're bringing in the very latest versions of lacinia and lacinia-pedestal (something we'll
likely do almost every chapter).

The main addition is the alaisi/postgres.async library, which is used to execute queries and other
operations against a PostgreSQL database.

lacinia-pedestal and postgres.async disagree on which version of
org.clojure/core.async [#async]_ to use, so we're pinning the version of core.async to
the very latest version available.

This kind of dependency version conflict is unfortunately a common occurance when different libraries are maintained by different groups on different schedules.
Fortunately, in this case, settling on a common version doesn't break either lacinia-pedestal or postgres.async.

Database Initialization
-----------------------

We've added a number of scripts to project.

First, a file used to start PostgreSQL:

.. ex:: database-1c docker-compose.yml

This file is used with the ``docker-compose`` command to set up one or more containers.
We only define a single container right now.

The ``image``  key identifies the name of the image to download from `hub.docker.com <http://hub.docker.com>`_.

The port mapping is part of the magic of Docker ... the PostgreSQL server, inside the container,
will listen to requests on its normal port: 5432, but our code, running on the host operation system,
can reach the server as port 25432 on ``localhost``.

The ``docker-up.sh`` script is used to start the container:

.. ex:: database-1 bin/docker-up.sh

There's also a ``bin/docker-down.sh`` script to shut down the container, and a ``bin/psql.sh`` to launch a SQL command
prompt for the ``cggdb`` database.

After starting the container, it is necessary to create the ``cggdb`` database and populate it with initial data, using
the ``setup-db.sh`` script:

.. ex:: database-1d bin/setup-db.sh

The DDL for the ``cggdb`` database includes a pair of timestamp columns, ``created_at`` and ``updated_at``, in most tables.
Defaults and database triggers ensure that these are maintained by PostgreSQL.

Primary Keys
------------

There's a problem with the data model we've used in prior chapters: the primary keys.

We've been using simple numeric strings as primary keys, because it was convenient.
Literally, we just made up those values.
But eventually, we're going to be writing data to the database, including new Board Games, new Publishers,
and new Members.

With the change to using PostgreSQL, we've switched to using numeric primary keys.
Not only are these more space efficient, but we have set up PostgreSQL to allocate them automatically.
This is great news once we have multiple Clojure Game Geek servers running, as it ensures that
primary keys are truly unique, enforced by the database.
We'll circle back to this issue when we add mutations to create new entities.

In the meantime, the schema has changed; the id fields have changed type from type ``ID`` (which, in GraphQL,
is a kind of opaque string) to type ``Int`` (which is a 32 bit, signed integer).

.. ex:: database-1b resources/cgg-schema.edn
  :emphasize-lines: 5, 34, 53, 66, 73, 84-85

In addition, the ``id`` field on the BoardGame, Member, and Publisher objects has been renamed: to ``game_id``, ``member_id``,
and ``publisher_id``, respectfully.
This aligns the field names with the database column names.

As Clojure developers, we generally follow the `kebab case` convention of using dashes in names.
GraphQL, JSON, and most databases use `snake case`, with underscores.
Snake case keywords in Clojure look slightly odd, but are 100% valid.

There's nothing that prevents you from reading database data and converting the column names to
kebab case ... but you'll just have to undo that somehow in the GraphQL schema, as kebab case is not valid
for GraphQL names.

Much better to have as consistent a representation of the data as possible, spanning the database,
the GraphQL schema, the Clojure data access code, and the over-the-wire JSON format ... and not buy yourself any extra work that
has no tangible benefits.

Database Connection
-------------------

In prior chapters, the ``:db`` component was just a wrapper around an Atom; starting here, we're going to
update it to be a wrapper around a connection to the PostgreSQL database running in the Docker container.

Our goal in this chapter is to update just one basic query to use the database,
the query that retrieves a game by its unique id.
We'll make just the changes necessary for that one query before moving on.

.. ex:: 4b9aec3656a5b4daa760709d50c26f9fe2b65608 src/clojure_game_geek/db.clj
   :emphasize-lines: 5, 7-22, 28-43

The requires for the ``db`` namespace have changed; we're using the ``postgres.async`` namespace to
connect to the database, and that entails using some ``clojure.core.async`` functions.

The ClojureGameGeekDb record has changed; it now has a ``conn`` (connection) field, and that is
the connection to the PostgreSQL database.
The ``start`` method now opens the connection to the database.

For the meantime, we've hardwired the connection details (hostname, username, password, and port) to our Docker container.
A later chapter will discuss approaches to configuration.
Also note that we're connecting to port ``25432`` on ``localhost``; Docker will forward that port to the container
port ``5432``.

We've added a private ``take!`` function [#bang]_; its purpose is to obtain the result of a query
against the database.
Because we are using the postgres.async library, when we perform a query or other
database operation, we don't block the current thread until results are ready.

Instead, the postgres.async functions return a core.async `channel`.

A full discussion of core.async will come later; for the moment, you can think of a channel
as similar to a promise; the query operation will run asynchronously in another thread,
and the result of the query operation will be `conveyed` through the channel.

The core.async ``<!!`` function blocks the current thread until a value is conveyed.
We've managed to turn an asynchronous operation back into a synchronous one ... once again, baby
steps.
A later chapter will discuss how to fully leverage asynchronous queries when using Lacinia.

A common convention with core.async channels is to convey either an actual result, or an exception
if something goes wrong.
That can happen here: if there's a problem executing the query, an exception will be conveyed
in the channel, instead of the expected sequence of row maps.

In the ``take!`` function, we check if the conveyed value is an exception, and throw it (in the
current thread) if so.

That leaves the revised implementation of the ``find-game-by-id`` function; the only data access function rewritten to use
the database connection.
It simply constructs and executes the SQL query.

With postgres.async the query is a vector
consisting of a SQL query string followed by zero or more query variables.
Each query variable is numbered from 1 and represented as ``$n`` in the SQL query string.

The ``query!`` function returns a channel, which is passed through ``take!`` to get
the results.
The results will be a sequence of maps, each map one matching row.
For this particular query, we are expecting a single match, so we can use ``first`` to return
just the map for the matching row.

If no rows match, then ``first`` will return nil.
That's a perfectly good way to identify that the provided Board Game id was not valid.

At the REPL
-----------

Starting a new REPL, we can give the new code and schema a test::

   (start)
   => :started
   (q "{ game_by_id(id: 1234) { game_id name summary min_players max_players }}")
   =>
   {:data {:game_by_id {:game_id 1234,
                        :name "Zertz",
                        :summary "Two player abstract with forced moves and shrinking board",
                        :min_players 2,
                        :max_players 2}}} min_players max_players }}")


Great! That works ... though all the other ``db`` namespace functions,
expecting to operate against an Atom, are now broken.
We'll fix them in the next couple of chapters.

User Namespace Improvements
---------------------------

We've made some tiny changes to the ``user`` namespace:

.. ex:: database-1b dev-resources/user.clj
   :emphasize-lines: 27, 37-55

To make loading and reloading the ``user`` namespace easier, we've changed the ``system`` Var to
be a ``defonce``.
This means that even if the code for the namespace is reloaded, the ``system`` Var will maintain
its value from before the code was reloaded.

A common cycle is to make code changes, ``stop``, then ``start`` the system.

We've moved the code that contructs a new system into the ``start`` function, and
changed the ``stop`` function to return the ``system`` Var to nil after stopping the system, if a system is
in fact running.

Lastly, there's a comment containing expressions to start and stop the system.
This is great for REPL oriented development, we can use the Cursive `send form before caret to REPL` command
(Shift-Ctrl E) [#emacs]_
to make it easier to quickly and accurately execute those commands.

Next Up
-------

We've been sloppy about one aspect of our application: we've entirely been testing at the REPL.
It's time to write some tests, then convert the rest of the ``db`` namespace.

.. [#container] A `Docker <https://www.docker.com/>`_ container is
   the  `Inception <http://www.imdb.com/title/tt1375666/>`_ of computers; a
   container is essentially a
   light-weight virtual machine that runs inside your computer.

   To the PostgreSQL server running inside the container, it will appear as if
   the entire computer is running Linux, just as if Linux and PostgreSQL were installed
   on a bare-metal computer.

   Docker images
   are smaller and less demanding than full operating system virtual machines. In fact
   frequently you will run several interconnected containers together.

   Docker includes infrastructure for downloading the images from a central repository.
   Ultimately, it's faster and easier to get PostgreSQL running
   inside a container that to install the database onto your computer.

.. [#async] core.async is a very powerful library for performing asynchronous computation
   in Clojure. We'll discuss core.async, and how it relates to Lacinia, in a later chapter.

.. [#bang] The Clojure naming convention is that names of unsafe functions end with a ``!``.

   Unsafe functions either have side effects, or may block the current thread.

   This largely applies to low-level functions, such as ``take!`` or ``<!!``.
   All of the data access functions, such as ``find-game-by-id`` are also unsafe, but
   are expected to be so by context, so their names don't end with ``!``.

.. [#emacs] The author uses Cursive, but Emacs and other editors all have similar functionality.
