External Database, Phase 2
==========================

Let's get the rest of the functions in the ``my.clojure-game-geek.db`` namespace
working again and add tests for them.
We'll do a little refactoring as well, to make both the production code
and the tests clearer and simpler.

Logging
-------

It's always a good idea to know exactly what SQL queries are executing in
your application; you'll never figure out what's slowing down your application
if you don't know what queries are even executing.

.. literalinclude:: /_examples/tutorial/db-4.clj
   :caption: src/my/clojure_game_geek/db.clj (partial)
   :emphasize-lines: 3-4,28-33
   :lines: 1-33

We've introduced our own version of ``clojure.java.jdbc/query`` that
logs the SQL and parameters before continuing on to the standard implementation.

Because of how we format the SQL in our code, it is useful to convert
the embedded newlines and indentation into single spaces.

A bit about logging: In a typical Java, or even Clojure, application
the focus on logging is on a textural message for the user to read.
Different developers approach this in different ways ... everything
from the inscrutably cryptic to the overly verbose.
Yet, across that spectrum, there always an assumption that some user is reading the log.

The ``io.pedestal/pedestal.log`` library introduces a different idea:
logs as a stream of data ... a sequence of maps.
That's what we see in the call to ``log/debug``: just keys and values
that are interesting.

When logged, it may look like::

    DEBUG my.clojure-game-geek.db - {:sql "select game_id, name, summary, min_players, max_players, created_at, updated_at from board_game where game_id = ?", :params (1234), :line 32}

That's the debug level and namespace, and the map of keys and values (``io.pedestal.log``
adds the ``:line`` key).

The useful and interesting details are present and unambiguously formatted,
since it is not formatted specifically for a user to read.

This can be a very powerful concept; these logs can even be read back
into memory, converted  back into data, and operated on with all the
``map``, ``reduce``, and ``filter`` power that Clojure provides. [#mapreduce]_

After years of sweating the details on formatting (and capitalizing, and quoting, and
punctuating) human-readible error messages, it is a joy to just throw whatever
data is useful into the log, and not care about all those human oriented formatting details.

This is, of course, all possible because all data in Clojure can be printed out nicely
and even read back in again.
By comparison, data values or other objects in Java
only have useful debugging output if their class provides
an override of the ``toString()`` method.

When it comes time to execute a query, little has changed
except that the call is now to the local ``query`` function, not the one
provided by ``clojure.java.jdcb``:

.. literalinclude:: /_examples/tutorial/db-4.clj
   :caption: src/my/clojure_game_geek/db.clj (partial)
   :lines: 42-49
   :emphasize-lines: 4

logback-test.xml
----------------

We can enable logging, just for testing purposes, in our ``logback-test.xml``:

.. literalinclude:: /_examples/tutorial/logback-test-2.xml
   :caption: dev-resources/logback-test.xml
   :emphasize-lines: 13

Nicely, Logback will pick up this change to the configuration file without
a restart.

Re-running tests
----------------

If we switch back to the ``my.clojure-game-geek.system-test`` namespace and re-run the tests,
the debug output will be mixed into the test tool output::

    Loading src/my/clojure_game_geek/db.clj... done
    Loading test/my/clojure_game_geek/system_test.clj... done
    Running tests in my.clojure-game-geek.system-test
    DEBUG my.clojure-game-geek.db - {:sql "select game_id, name, summary, min_players, max_players, created_at, updated_at from board_game where game_id = ?", :params (1234), :line 31}
    Ran 1 test containing 1 assertion.
    No failures.

More code updates
-----------------

The remaining functions in ``my.clojure-game-geek.db`` can be rewritten to make use of ``query`` and
operate on the real database:

.. literalinclude:: /_examples/tutorial/db-5.clj
   :caption: src/my/clojure_game_geek/db.clj
   :lines: 49-

The majority of this is quite straight-forward, except for
``upsert-game-rating``, which makes use of PostgreSQL language extensions
to handle a conflict (where a rating already exists for a particular
game and member) - the insert is converted to an update.

In the next chapter, we'll focus on testing the code we've just added.

.. [#mapreduce] I've used this on another project where a bug manifested only
   at a large scale of operations; by hooking into Logback and capturing the
   logged maps, it was possible to quickly filter through megabytes of output
   to the find the clues that revealed how the bug occured.
