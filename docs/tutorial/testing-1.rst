Testing, Phase 1
================

Before we get much further, we are very far along for code that has no tests.  Let's fix that.

First, we need to reorganize a couple of things, to make testing easier.

Dependencies
------------


.. ex:: 593e5e55aa57aa4bd2d30eb8dd0ebf8e810cd197 project.clj
   :emphasize-lines: 6-12

To start with, we're updating all our dependencies to the (at time of writing) latest versions.
This includes bumping up the latest Clojure release, 1.9.

HTTP Port
---------

Let's save ourselves some frustration: when we run our tests, we can't know if there
is a REPL-started system running or not.
There's no problem with two complete system maps running at the same time, and even
hitting the same database ... that's why we like Component, as it helps us avoid unecessary globals.

Unfortunately, we still have one conflict: HTTP port.
Only one of the systems can bind to the default 8888 port, so let's make sure our tests use
a different port.

.. ex:: 7783822f9b2606d95b5db79391e60a444fd05dac src/clojure_game_geek/server.clj
   :emphasize-lines: 6,12-13,23

We've added a bit of configuration for the ``:server`` component, the port to bind to.
This will make it possible for our test code to use a different port.

Simplify Utility
----------------

To keep our tests simple, we'll want to use the ``simplify`` utility function discussed earlier.
Here, we're creating a new namespace for test utilities:

.. ex:: 4388d0d7974e498fb600da77a2d7915c8e8a0812 dev-resources/clojure_game_geek/test_utils.clj

This is located in the ``dev-resource`` folder, so that it won't be confused for tests.

Over time, we're likely to add a number of little tools here to make tests more clear and concise.

Integration or Unit?  Yes
-------------------------

When it comes to testing, your first thought should be at what level of granularity testing should occur.
`Unit testing` is generally testing the smallest possible bit of code; in Clojure terms, testing a single
function.

`Integration testing` is at a higher level, testing how several elements of the system work together.

Our application is layered as follows:

.. graphviz::

    digraph {

      graph [rankdir=LR];

      client [label="External Client"]
      fieldresolver [label="Field Resolver\nfunction"]
      dbaccess [label="clojure-game-geek.db\nfunction"]

      client -> HTTP -> Pedestal -> Lacinia -> fieldresolver -> dbaccess -> DB

    }

In theory, we could test each layer separately;  that is, we could test the
``clojure-game-geek.db`` functions against a database (or even, so mockup of a database),
then test the field resolver functions against the ``db`` functions, etc.

In practive, building a Lacinia application is an exercise in interaction; the individual bits
of code are often quite small, but there can be issues with how they interaction, so I prefer
a modest amount of integration testing.

There's still a place for more focused testing, especially testing of edge cases and failure
scenarios.

Likewise, as we build up more code in our application outside of Lacinia, such as request
authentication and authorization, we may want to exercise our code by sending HTTP requests in
from the tests.

For our first test, we'll do some integration testing; our tests will start at the
Lacinia step, and work all the way down to the database instance (running in our Docker container).

To that mind, we want to start up the schema connected to field resolvers, the ``db`` namespace,
and the database itself.
The easiest way to do this start up a new system, and extract the pieces we need.

First Test
----------

Our first test will replicate a bit of the manual testing we've already done in the REPL: reading
an existing board game by its primary key.

.. ex:: 7783822f9b2606d95b5db79391e60a444fd05dac test/clojure_game_geek/system_tests.clj

We're making use of the standard ``clojure.test`` library.

The ``test-system`` function builds a standard system, but overrides the HTTP port, as dicussed above.

We use that function to create and start a system for our first test.
This first test is a bit verbose; later we'll refactor some of the code out of it, to make writing
additional tests easier.

Because we control the initial test data [#testdata]_ we know what at least a couple of rows
in our database look like.

It's quite easy to craft a tiny GraphQL query and execute it; that will flow through Lacinia, to
our field resolvers, to the database access code, and ultimately to the database, just like
the chart above.

Running the Tests
-----------------

There's a number of ways to run tests.

From the command line, ``lein test``::

   ~/workspaces/github/clojure-game-geek > lein test

   lein test clojure-game-geek.system-tests

   Ran 1 tests containing 1 assertions.
   0 failures, 0 errors.


But who wants to do that all the time?

Clojure startup time is somewhat slow, as before your tests can run, large numbers of Java classes
must be loaded, and signifcant amounts of Clojure code, both from our applicationo and in any libraries, must
be read, parsed, and compiled.

If you are using an IDE, you will be able to run tests directly in a running REPL.
In Cursive, :kbd:`Ctrl-Shift-T` runs all tests in the current namespace, and
:kbd:`Ctrl-Alt-Cmd-T` runs just the test under the cursor.

Similar commands exist for whichever editor you are using.
Being able to load code and run tests is a fraction of a second is incredibly liberating if you are
used to a more typical grind of starting a new process just to run tests [#twitter]_ .

Database Issues
---------------

These tests assume the database is running locally, and has been initialized.


What if it's not?  It might look like this::

   ~/workspaces/github/clojure-game-geek > lein test

   lein test clojure-game-geek.system-tests

   lein test :only clojure-game-geek.system-tests/can-read-board-game

   ERROR in (can-read-board-game) (SocketChannelImpl.java:-2)
   Uncaught exception, not in assertion.
   expected: nil
     actual: java.net.ConnectException: Connection refused: localhost/127.0.0.1:25432
    at sun.nio.ch.SocketChannelImpl.checkConnect (SocketChannelImpl.java:-2)
       sun.nio.ch.SocketChannelImpl.finishConnect (SocketChannelImpl.java:717)
       io.netty.channel.socket.nio.NioSocketChannel.doFinishConnect (NioSocketChannel.java:330)
       io.netty.channel.nio.AbstractNioChannel$AbstractNioUnsafe.finishConnect (AbstractNioChannel.java:338)
       io.netty.channel.nio.NioEventLoop.processSelectedKey (NioEventLoop.java:580)
       io.netty.channel.nio.NioEventLoop.processSelectedKeysOptimized (NioEventLoop.java:504)
       io.netty.channel.nio.NioEventLoop.processSelectedKeys (NioEventLoop.java:418)
       io.netty.channel.nio.NioEventLoop.run (NioEventLoop.java:390)
       io.netty.util.concurrent.SingleThreadEventExecutor$5.run (SingleThreadEventExecutor.java:742)
       io.netty.util.concurrent.DefaultThreadFactory$DefaultRunnableDecorator.run (DefaultThreadFactory.java:145)
       java.lang.Thread.run (Thread.java:748)

   Ran 1 tests containing 1 assertions.
   0 failures, 1 errors.
   Tests failed.

Conclusion
----------

We've created one test, and managed to get it to run.
That's a great start.
Next up, we'll flesh out our tests, fix the many outdated
functions in ``clojure-game-geek.db``, and do some refactoring to ensure that our tests
are concise, readable, and efficient.

.. [#testdata] An improved approach might be to create a fresh database namespace for each test, or
   each test namespace, and create and populate the tables with fresh test data each time.

.. [#twitter] Downside: you'll probably read a lot less Twitter while developing.


