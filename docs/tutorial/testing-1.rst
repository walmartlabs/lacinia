Testing, Phase 1
================

Before we get much further, we are very far along for code that has no tests.  Let's fix that.

First, we need to reorganize a couple of things, to make testing easier.

HTTP Port
---------

Let's save ourselves some frustration: when we run our tests, we can't know if there
is a REPL-started system running or not.
There's no problem with two complete system maps running at the same time, and even
hitting the same database, all within a single process
... that's why we like Component, as it helps us avoid unecessary globals.

Unfortunately, we still have one conflict: the HTTP port for inbound requests.
Only one of the systems can bind to the default 8888 port, so let's make sure our tests use
a different port.

.. literalinclude:: /_examples/tutorial/server-2.clj
   :caption: src/my/clojure_game_geek/server.clj
   :emphasize-lines: 6,12-14,24

We've added a bit of configuration for the ``:server`` component, the port to bind to.
This will make it possible for our test code to use a different port.

Simplify Utility
----------------

To keep our tests simple, we'll want to use the ``simplify`` utility function discussed earlier.
Here, we're creating a new namespace for test utilities, and moving the ``simplify`` function
from the ``user`` namespace to the ``test-utils`` namespace:

.. literalinclude:: /_examples/tutorial/test_utils-1.clj
   :caption: dev-resources/clojure_game_geek/test_utils.clj

This is located in the ``dev-resource`` folder, so that that Leiningen
won't treat it as a namespace containing tests to execute.

Over time, we're likely to add a number of little tools here to make tests more clear and concise.

Integration or Unit?  Yes
-------------------------

When it comes to testing, your first thought should be at what level of granularity testing should occur.
`Unit testing` is generally testing the smallest possible bit of code; in Clojure terms, testing a single
function, ideally isolated from everything else.

`Integration testing` is testing at a higher level, testing how several elements of the system work together.

Our application is layered as follows:

.. graphviz::

    digraph {

      graph [rankdir=LR];

      client [label="External Client"]
      fieldresolver [label="Field Resolver\nfunction"]
      dbaccess [label="clojure-game-geek.db\nfunction"]

      client -> Pedestal [label="HTTP"]
      Pedestal -> Lacinia -> fieldresolver -> dbaccess -> PostgreSQL

    }

In theory, we could test each layer separately;  that is, we could test the
``my.clojure-game-geek.db`` functions against a database (or even, some mockup of a database),
then test the field resolver functions against the ``db`` functions, etc.

In practice, building a Lacinia application is an exercise in integration; the individual bits
of code are often quite small and simple, but there can be issues with how these bits of code interact.

I prefer a modest amount of integration testing using a portion of the full stack.

There's no point in testing a block of database code, only to discover that the results
don't work with the field resolver functions calling that code.
Likewise, for nominal success cases, there's no point in testing the raw database code if
the exact same code will be exercised when testing the field resolver functions.

There's still a place for more focused testing, especially testing of failure
scenarios and other edge cases.

Likewise, as we build up more code in our application outside of Lacinia, such as request
authentication and authorization, we may want to exercise our code by sending HTTP requests in
from the tests.

For our first test, we'll do some integration testing; our tests will start at the
Lacinia step from the diagram above, and work all the way down to the database instance (running in our Docker container).

To that mind, we want to start up the schema connected to field resolvers, the ``db`` namespace,
and the database itself.
The easiest way to do this start up a new system, and extract the pieces we need from the running system map.

First Test
----------

Our first test will replicate a bit of the manual testing we've already done in the REPL: reading
an existing board game by its primary key.

.. literalinclude:: /_examples/tutorial/system_tests-1.clj
   :caption: test/clojure_game_geek/system_tests.clj

We're making use of the standard ``clojure.test`` library.

The ``test-system`` function builds a standard system, but overrides the HTTP port, as dicussed above.

We use that function to create and start a system for our first test.
This first test is a bit verbose; later we'll refactor some of the code out of it, to make writing
additional tests easier.

Because we control the initial test data [#testdata]_ we know what at least a couple of rows
in our database look like.

It's quite easy to craft a tiny GraphQL query and execute it; that will flow through Lacinia, to
our field resolvers, to the database access code, and ultimately to the database, just like
in the diagram.

Running the Tests
-----------------

There's a number of ways to run Clojure tests.

From the command line, ``lein test``::

   ~/workspaces/github/clojure-game-geek > lein test

   lein test clojure-game-geek.system-tests

   Ran 1 tests containing 1 assertions.
   0 failures, 0 errors.


But who wants to do that all the time?

Clojure startup time is somewhat slow, as before your tests can run, large numbers of Java classes
must be loaded, and signifcant amounts of Clojure code, both from our application and from any libraries, must
be read, parsed, and compiled.

Fortunately, Clojure was created with a REPL-oriented development workflow in mind.
This is a fast-feedback cycle, where you can run tests, diagnose failures, make code corrections,
and re-run the tests in a matter of seconds.
Generally, the slowest part of the loop is the part that executes inside your grey matter.

Because the Clojure code base is already loaded and running, even a change that affects many namespaces
can be reloaded in milliseconds.

If you are using an IDE, you will be able to run tests directly in a running REPL.
In Cursive, :kbd:`Ctrl-Shift-T` runs all tests in the current namespace, and
:kbd:`Ctrl-Alt-Cmd-T` runs just the test under the cursor.
Cursive is even smart enough to properly reload all modified namespaces before executing the tests.

Similar commands exist for whichever editor you are using.
Being able to load code and run tests in a fraction of a second is incredibly liberating if you are
used to a more typical grind of starting a new process just to run tests [#twitter]_ .

Database Issues
---------------

These tests assume the database is running locally, and has been initialized.


What if it's not?  It might look like this::

   lein test clojure-game-geek.system-tests
   WARN  com.mchange.v2.resourcepool.BasicResourcePool - com.mchange.v2.resourcepool.BasicResourcePool$ScatteredAcquireTask@614dbaad -- Acquisition Attempt Failed!!! Clearing pending acquires. While trying to acquire a needed new resource, we failed to succeed more than the maximum number of allowed acquisition attempts (30). Last acquisition attempt exception:
   org.postgresql.util.PSQLException: Connection to localhost:25432 refused. Check that the hostname and port are correct and that the postmaster is accepting TCP/IP connections.
           at org.postgresql.core.v3.ConnectionFactoryImpl.openConnectionImpl(ConnectionFactoryImpl.java:280)
           at org.postgresql.core.ConnectionFactory.openConnection(ConnectionFactory.java:49)
           at org.postgresql.jdbc.PgConnection.<init>(PgConnection.java:195)
           at org.postgresql.Driver.makeConnection(Driver.java:454)
           at org.postgresql.Driver.connect(Driver.java:256)
           at com.mchange.v2.c3p0.DriverManagerDataSource.getConnection(DriverManagerDataSource.java:175)
           at com.mchange.v2.c3p0.WrapperConnectionPoolDataSource.getPooledConnection(WrapperConnectionPoolDataSource.java:220)
           at com.mchange.v2.c3p0.WrapperConnectionPoolDataSource.getPooledConnection(WrapperConnectionPoolDataSource.java:206)
           at com.mchange.v2.c3p0.impl.C3P0PooledConnectionPool$1PooledConnectionResourcePoolManager.acquireResource(C3P0PooledConnectionPool.java:20
   ...

   Ran 1 tests containing 1 assertions.
   0 failures, 1 errors.
   Tests failed.

Because of the connection pooling, this actually takes quite some time
to fail, and produces hundreds (!) of lines of exception output.

If you see a huge swath of tests failing, the first thing to do is double check external dependencies,
such as the database running inside the Docker container.

Conclusion
----------

We've created just one test, and managed to get it to run.
That's a great start.
Next up, we'll flesh out our tests, fix the many outdated
functions in the ``my.clojure-game-geek.db`` namespace,
and do some refactoring to ensure that our tests are concise, readable, and efficient.

.. [#testdata] An improved approach might be to create a fresh database namespace for each test, or
   each test namespace, and create and populate the tables with fresh test data each time.
   This might be very important when attempting to run these tests inside a Continuous Integration
   server.

.. [#twitter] Downside: you'll probably read a lot less Twitter while developing.
