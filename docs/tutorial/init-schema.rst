Initial Schema
==============

At this stage, we're still just taking baby steps, and getting our bearings.

By the end of this stage, we'll have a minimal schema and be able to execute our first query.

Schema EDN File
---------------

We're going to define an initial schema for our application that
matches the :doc:`domain`.

Our initial schema is just for the BoardGame entity, and a single operation to retrieve
a game by its id:

.. literalinclude:: /_examples/tutorial/cgg-schema-1.edn
   :caption: resources/cgg-schema.edn

.. sidebar:: Details

  See documentation about :doc:`/objects`, :doc:`/fields`, and :doc:`/queries`.

A Lacinia schema is an `EDN <https://github.com/edn-format/edn>`_ file.
It is a map of maps; the top level keys identify the type of definition: ``:objects``,
``:interfaces``, ``:enums``, and so forth.
The inner maps are from keywords to a type-specific structure.

Query is a special object that contains the GraphQL queries that
a client can execute.
This schema defines a single query, ``gameById``, that returns an object as defined by the
``BoardGame`` type.

A schema is declarative: it defines what operations are possible, and what types and fields exist,
but has nothing to say about where any of the data comes from.
In fact, Lacinia has no opinion about that either!
GraphQL is a contract between a consumer and a provider for how to request
and present data, it's not any form of database layer, object relational mapper, or anything
similar.

Instead, Lacinia handles the parsing of a client query, and guides
the execution of that query, ultimately invoking application-specific callback hooks:
:doc:`field resolvers </resolve/index>`.
Field resolvers are the only source of actual data.
Ultimately, field resolvers are simple Clojure functions, but those can't, and shouldn't, be
expressed inside an EDN file.

Later we'll see how to connect fields, such as ``gameById`` to a field resolver.

We've made liberal use of the ``:description`` property in the schema.
These descriptions are intended for developers who will make use of your
GraphQL interface.
Descriptions are the equivalent of doc-strings on Clojure functions, and we'll see them
show up later when we :doc:`discuss GraphiQL <pedestal>`.
It's an excellent habit to add descriptions early, rather than try and go back
and add them in later.

We'll add more fields, more types, relationships between types, and more operations
in later chapters.

We've also demonstrated the use of a few Lacinia conventions in our schema:

* Built-in scalar types, such as ID, String, and Int are referenced as
  symbols. [#internal]_

* Schema-defined types, such as ``:BoardGame``, are referenced as keywords.

* Fields are lower-case names, and types are CamelCase.

In addition, all GraphQL names (for fields, types, and so forth) must contain only alphanumerics
and the underscore.
The dash character is, unfortunately, not allowed.
If we tried to name the query ``query-by-id``, Lacinia would throw a `clojure.spec <https://clojure.org/guides/spec>`_ validation exception when we attempted
to use the schema. [#spec]_

In Lacinia, there are base types, such as ``String`` and ``:BoardGame`` and wrapped types, such
as ``(non-null String)``.
The two wrappers are ``non-null`` (a value *must* be present) and
``list`` (the type is a list of values, not a single value).
These can even be combined!

Notice that the return type of the ``gameByID`` query is ``:BoardGame`` and `not`
``(non-null :BoardGame)``.
This is because we can't guarantee that a game can be resolved, if the id provided in the client query is not valid.
If the client provides an invalid id, then the result will be nil, and that's not considered an error.

In any case, this single BoardGame entity is a good starting point.

schema namespace
----------------

With the schema defined, the next step is to write code to load the schema into memory, and make it operational for queries:

.. literalinclude:: /_examples/tutorial/schema-0.clj
   :caption: src/my/clojure_game_geek/schema.clj

This code loads the schema EDN file, :doc:`injects field resolvers </resolve/attach>` into the schema,
then `compiles` the schema.
The compilation step is necessary before it is possible to execute queries.
Compilation reorganizes the schema, computes various defaults, performs verifications,
and does a number of other necessary steps.

The ``inject-resolvers`` function updates the schema, adding `:resolve` keys to fields.  The keys of the map identify a type and a field,
and the value is the resolver function.

The field resolver in this case is just a temporary placeholder; it ignores all the arguments
passed to it, and simply returns nil.
Like all field resolver functions, it accepts three arguments: a context map,
a map of field arguments, and a container value.
We'll discuss what these are and how to use them shortly.

user namespace
--------------

.. sidebar:: Not too much!

   An annoyance with putting code into the ``user`` namespace is that you can't
   start a new REPL unless and until the ``user`` namespace loads.
   Every so often, you have to go in your ``user`` namespace and comment everything out just to get
   a REPL running, to start debugging an error elsewhere.

A key advantage of Clojure is REPL-oriented [#repl]_ development: we want to be able to
run our code through its paces almost as soon as we've written it - and when we
change code, we want to be able to try out the changed code instantly.

Clojure, by design, is almost uniquely good at this interactive style of development.
Features of Clojure exist just to support REPL-oriented development, and its one of the ways
in which using Clojure will vastly improve your productivity!

We can add a bit of scaffolding to the ``user`` namespace, specific to
our needs in this project.
When you launch a REPL, it always starts in this namespace.

The :file:`user.clj` needs to be on the classpath, but shouldn't be packaged when we eventually build a Jar from our project.  We need
to introduce a new `alias` in the :file:`deps.edn` for this.

An alias is used to extend the base dependencies with more information about
running the project; this includes extra source paths, extra dependencies,
and extra configuration about what function to run at startup.

We're going to start by adding a ``:dev`` alias:

.. literalinclude:: /_examples/tutorial/deps-3.edn
   :caption: deps.edn
   :emphasize-lines: 15


We can now define the user namespace in the ``dev-resources`` folder; this ensures
that it is not included with the rest of our application when we eventually package
and deploy the application.

.. literalinclude:: /_examples/tutorial/user-1.clj
   :caption: dev-resources/user.clj

The key function is ``q``, which invokes :api:`/execute`.

We'll use that to test GraphQL queries against our schema and see the results
directly in the REPL: no web browser necessary!

With all that in place, we can launch a REPL and try it out::

    > clj -M:dev
    Clojure 1.11.1
    user=> (q "{ gameById(id: \"foo\") { id name summary }}")
    {:data #ordered/map ([:gameById nil])}
    user=>

The ``clj -M:dev`` indicates that a REPL should be started that includes the ``:dev`` alias; this is what adds :file:`dev-resources` to the classpath and the ``user`` namespace is then loaded from :file:`dev-resources/user.clj`.

We get an odd result when executing the query; not a map but that strange ``#ordered/map`` business.

This is because  value for the ``:data`` key makes use of an ordered map - a map that always orders its keys in the exact order that they are added to the map.
That's part of the GraphQL
specification: the order in which things appear in the query dictates the order in which
they appear in the result.  Clojure's map implementations don't always keep keys in the order they are added.

In any case, this result is equivalent to ``{:data {:gameById nil}}``.

That's as it should be: the resolver was unable to resolve the provided id
to a BoardGame, so it returned nil.
This is not an error ... remember that we defined the type of the
``gameById`` query to allow nulls, just for this specific situation.

However, Lacinia still returns a map with the operation name and operation selection.
Failure to return a result with a top-level ``:data`` key would signify an error executing
the query, such as a parse error.
That's not the case here at all - the lack of a value is not an error.

Summary
-------

We've defined an exceptionally simple schema in EDN, but still have managed to load it
into memory and compile it.
We've also used the REPL to execute a query against the schema and seen the initial
(and quite minimal) result.

In the next chapter, we'll build on this modest start, introducing more schema types, and a few helpers to keep our code clean and easily testable.



.. [#internal] Internally, `everything` is converted to keywords, so if you prefer
   to use symbols everywhere, nothing will break. Conversion to keyboards is one part of the schema compilation
   process.

.. [#spec] Because the input schema format is so complex, it is `always` validated
   using clojure.spec. This helps to ensure that minor typos or other gaffes
   are caught early rather than causing you great confusion later.

.. [#repl] Read Eval Print Loop: you type in an expression, and Clojure evaluates and
   prints the result.  This is an innovation that came early to Lisps,
   and is integral to other languages such as Python, Ruby, and modern JavaScript.
   Stuart Halloway has a talk, `Running with Scissors: Live Coding With Data <https://www.youtube.com/watch?v=Qx0-pViyIDU>`_,
   that goes into a lot more detail on how important and useful the REPL is.
