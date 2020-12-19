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

.. literalinclude:: /_examples/tutorial/project-5.clj
   :caption: project.clj
   :emphasize-lines: 6,8-

We're bringing in the latest versions of lacinia and lacinia-pedestal available at the time
this page was written (something we'll
likely do almost every chapter).
Since these are now based on Clojure 1.9, it's a fine time to upgrade
to that.

We're also adding several new dependencies for accessing a PostgreSQL database:

* The Clojure ``java.jdbc`` library
* The PostgreSQL driver that plugs into the library
* A java library, ``c3p0``, that  is used for connection pooling



Database Initialization
-----------------------

We've added a number of scripts to project.

First, a file used to start PostgreSQL:

.. literalinclude:: /_examples/tutorial/docker-compose-1.yml
   :caption: docker-compose.yml

This file is used with the ``docker-compose`` command to set up one or more containers.
We only define a single container right now.

The ``image``  key identifies the name of the image to download from `hub.docker.com <http://hub.docker.com>`_.

The port mapping is part of the magic of Docker ... the PostgreSQL server, inside the container,
will listen to requests on its normal port: 5432, but our code, running on the host operation system,
can reach the server as port 25432 on ``localhost``.

The ``docker-up.sh`` script is used to start the container:

.. literalinclude:: /_examples/tutorial/docker-up-1.sh
   :caption: bin/docker-up.sh

There's also a ``bin/docker-down.sh`` script to shut down the container, and a ``bin/psql.sh`` to launch a SQL command
prompt for the ``cggdb`` database.

After starting the container, it is necessary to create the ``cggdb`` database and populate it with initial data, using
the ``setup-db.sh`` script:

.. literalinclude:: /_examples/tutorial/setup-db-1.sh
   :caption: bin/setup-db.sh

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

.. literalinclude:: /_examples/tutorial/cgg-schema-6.edn
  :caption: resources/cgg-schema.edn
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

org.clojure/java.jdbc
---------------------

This library is the standard approach to accessing a database from Clojure code.
``java.jdbc`` can access, in a uniform manner, any database for which there is a Java JDBC driver.

The ``clojure.java.jdbc`` namespace contains a number of functions for acessing a database, including
functions for executing arbitrary queries, and specialized functions for peforming inserts, updates, and deletes.

For all of those functions, the first parameter is a `database spec`, a map of data used to connect to the database.
In a trivial case, this identifies the Java JDBC driver class, and provides extra information to build a JDBC URL, including
details such as the database host, the user and password, and the name of the database.

In practice, opening up a new connection for each operation is unacceptible so we'll jump right in with a
database connection pooling library, ``C3P0``.

clojure-game-geek.db
--------------------

In prior chapters, the ``:db`` component was just a wrapper around an Atom; starting here, we're going to
update it to be a wrapper around a connection to the PostgreSQL database running in the Docker container.

Our goal in this chapter is to update just one basic query to use the database,
the query that retrieves a board game by its unique id.
We'll make just the changes necessary for that one query before moving on.

.. literalinclude:: /_examples/tutorial/db-3.clj
   :caption: src/clojure_game_geek/db.clj
   :emphasize-lines: 3-26,33-38

The requires for the ``db`` namespace have changed; we're using the ``clojure.java.jdbc`` namespace to
connect to the database and execute queries, and also making use of the ``ComboPooledDataSource`` class,
which allows for pooled connections.

The ClojureGameGeekDb record has changed; it now has a ``ds`` (data source) field, and that is
the connection to the PostgreSQL database.
The ``start`` method now opens the connection pool to the database, and the
``stop`` method shuts down the connection pool.

For the meantime, we've hardwired the connection details (hostname, username, password, and port) to our Docker container.
A later chapter will discuss approaches to configuration.
Also note that we're connecting to port ``25432`` on ``localhost``; Docker will forward that port to the container
port ``5432``.

That leaves the revised implementation of the ``find-game-by-id`` function; the only data access function rewritten to use
the database connection.
It simply constructs and executes the SQL query.

With ``clojure.java.jdbc`` the query is a vector
consisting of a SQL query string followed by zero or more query parameters.
Each ``?`` character in the query string corresponds to a query parameter, based on position.

The ``clojure.java.jdbc/query`` function returns a seq of matching rows.
By default, each selected row is converted into a Clojure map, and the column names are
converted from strings into keywords.

For an operation like this one, which returns at most one map, we use ``first``.

If no rows match, then the seq will be empty, and ``first`` will return nil.
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
                        :max_players 2}}}")


Great! That works!

Notice how everything fits together: the column names in the database (``game_id``, ``summary``, etc.)
became keywords (``:game_id``,  ``:summary``, etc.) in a map; meanwhile the GraphQL field names did the same
conversion and everything meets together in the middle, with GraphQL fields selecting those same keys from the map.

Meanwhile all the other ``clojure-game-geek.db`` namespace functions,
expecting to operate against a map inside an Atom, are now broken.
We'll fix them in the next couple of chapters.

User Namespace Improvements
---------------------------

We've made some tiny changes to the ``user`` namespace:

.. literalinclude:: /_examples/tutorial/user-5.clj
   :caption: dev-resources/user.clj
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

.. [#emacs] The author uses Cursive, but Emacs and other editors all have similar functionality.
