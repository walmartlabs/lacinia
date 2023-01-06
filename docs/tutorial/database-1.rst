External Database, Phase 1
==========================

We've gone pretty far with our application so far, but it's time to make that big leap, and convert
things over to an actual database.
We'll be running `PostgreSQL <https://www.postgresql.org/>`_ inside a
`Docker <https://www.docker.com/>`_ container.

We're definitely going to be taking two steps backward before taking further steps forward, but the majority of the changes
will be in the ``my.clojure-game-geek.db`` namespace; the majority of the application, including the
field resolvers, will be unaffected.

Dependency Changes
------------------

.. literalinclude:: /_examples/tutorial/deps-6.edn
   :caption: deps.edn
   :emphasize-lines: 5-7

This adds several new dependencies for accessing a PostgreSQL database:

* The Clojure ``java.jdbc`` library
* The PostgreSQL driver that plugs into the library
* A java library, ``c3p0``, that  is used for connection pooling


Database Initialization
-----------------------

We'll be using Docker's compose functionality to start and stop the container.

.. literalinclude:: /_examples/tutorial/docker-compose-1.yml
   :caption: docker-compose.yml

The ``image``  key identifies the name of the image to download from `hub.docker.com <http://hub.docker.com>`_.  Postgres requires you to provide a server-level password as well, that's specified in the service's environment.

The port mapping is part of the magic of Docker ... the PostgreSQL server, inside the container,
will listen to requests on its normal port: 5432, but our code, running on the host operation system,
can reach the server as port 25432 on ``localhost``.


To start working with the database, we'll let Docker start it::

    > docker compose up -d
    [+] Running 2/2
     ⠿ Network clojure-game-geek_default  Created                                                                                          0.0s
     ⠿ Container clojure-game-geek-db-1   Started                                                                                          0.3s

You'll see Docker download the necessary Docker images the first time you execute this.

The ``-d`` argument detaches the container from the terminal, otherwise PostgreSQL would write output to your terminal, and would shut down if you hit :kbd:`Ctrl-C`.

Later, you can shut down this detached container with ``docker compose down``.


There's also `bin/psql.sh`` to launch a SQL command prompt for the  database (not shown here).

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
We'll circle back to this issue when we add mutations to create new entities.

In the meantime, our database schema uses numeric primary keys, so we'll need
to make changes to the GraphQL schema to match [#incompatible]_; the id fields have changed type from type ``ID`` (which, in GraphQL,
is a kind of opaque string) to type ``Int`` (which is a 32 bit, signed integer).

.. literalinclude:: /_examples/tutorial/cgg-schema-6.edn
  :caption: resources/cgg-schema.edn
  :emphasize-lines: 5,33,50,63,69,77-78

In addition, the ``id`` field on the BoardGame, Member, and Publisher objects has been renamed: to ``game_id``, ``member_id``,
and ``publisher_id``, respectfully.
This will be handy when performing joins across tables.

org.clojure/java.jdbc
---------------------

This library is the standard approach to accessing a database from Clojure code.
``java.jdbc`` can access, in a uniform manner, any database for which there is a Java JDBC driver.

The ``clojure.java.jdbc`` namespace contains a number of functions for acessing a database, including
functions for executing arbitrary queries, and specialized functions for peforming inserts, updates, and deletes.

For all of those functions, the first parameter is a `database spec`, a map of data used to connect to the database, to perform the desired query or other operation.

The spec is normally a map with many different options for what keys to specify.

In a trivial case, the spec identifies the Java JDBC driver class, and provides extra information to build a JDBC URL, including
details such as the database host, the user name and password, and the name of the database.

In practice, opening up a new connection for each operation has unacceptible performance, so we'll jump right in with a
database connection pooling library, C3P0.

``java.jdbc`` supports this with the ``:datasource`` key in the spec.
A class in C3P0 implements the ``javax.sql.DataSource`` interface,
making it compatible with ``java.jdbc``.

my.clojure-game-geek.db
-----------------------

In prior chapters, the ``:db`` component was just a wrapper around an Atom; starting here, we're going to
update it to be a wrapper around a pooled connection pool to the PostgreSQL database running in the Docker container.

Our goal in this chapter is to update just one basic query to use the database,
the query that retrieves a board game by its unique id.
We'll make just the changes necessary for that one query before moving on.

.. literalinclude:: /_examples/tutorial/db-3.clj
   :caption: src/my/clojure_game_geek/db.clj
   :emphasize-lines: 2-16,21,24-41

The requires for the ``db`` namespace have changed; we're using the ``clojure.java.jdbc`` namespace to
connect to the database and execute queries, and also making use of the ``ComboPooledDataSource`` class,
which allows for pooled connections.

The ClojureGameGeekDb record has changed; it now has a ``datasource`` field, and that is
the connection pool for the PostgreSQL database.
The ``start`` method now creates the connection pool
``stop`` method shuts down the connection pool.

For the meantime, we've hardwired the connection details (hostname, username, password, and port) to our Docker container.
A later chapter will discuss approaches to configuration.
Also note that we're connecting to port ``25432`` on ``localhost``; Docker will forward that port to the container
port ``5432``, which is the PostgreSQL server listens to.

By the time the ``start`` method completes, the ``:db`` component is in
the correct shape to be passed as a ``clojure.java.jdbc`` database spec.

That leaves the revised implementation of the ``find-game-by-id`` function; the only data access function so far rewritten to use
the database.
It simply constructs and executes the SQL query.

With ``clojure.java.jdbc`` the query is a vector
consisting of a SQL query string followed by zero or more query parameters.
Each ``?`` character in the query string corresponds to a query parameter, based on position.

The ``query`` function returns a seq of matching rows.
By default, each selected row is converted into a Clojure map, and the column names are
converted from strings into keywords.

For an operation like this one, which returns at most one map, we use ``first``.

Further, we remap the keys from their database snake_case names, to their GraphQL camelCase names, where necessary.

If no rows match, then the seq will be empty, and ``first`` will return nil.
That's a perfectly good way to identify that the provided Board Game id was not valid.

At the REPL
-----------

Starting a new REPL, we can give the new code and schema a test::

    (start)
    => :started
    (q "{ gameById(id: 1234) { id name summary minPlayers maxPlayers }}")
    =>
    {:data {:gameById {:id 1234,
                       :name "Zertz",
                       :summary "Two player abstract with forced moves and shrinking board",
                       :minPlayers 2,
                       :maxPlayers 2}}}


Great! That works!

Meanwhile all the other ``my.clojure-game-geek.db`` namespace functions,
expecting to operate against a map inside an Atom, are now broken.
We'll fix them in the next couple of chapters.

Summary
-------

We now have our application working against a live PostgreSQL database and one operation actually works.  However, we've been sloppy about a key part of our
application development: we've entirely been testing at the REPL.

In the next chapter, we'll belatedly write some tests, then convert the rest of the ``db`` namespace to use the database.

.. [#incompatible] This kind of change is very incompatible - it could easily break
   clients that expect fields and arguments to still be type ID, and should only be
   considered before a schema is released in any form.
