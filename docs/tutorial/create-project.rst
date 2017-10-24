Creating the Project
====================

In this first step of the tutorial, we'll create the initial empty project, and set up the
initial dependencies.

Let's get started.

The first step is to create a new, empty project::

  12:51:44 ~/workspaces/github > lein new clojure-game-geek
  Generating a project called clojure-game-geek based on the 'default' template.
  The default template is intended for library projects, not applications.
  To see other templates (app, plugin, etc), try `lein help new`.

Although Leiningen has an ``app`` template, it is not significantly different than the default
template.

Normally, we might delete some files we don't need, such as `.hgignore` and the default `doc` directory,
but for simplicity we'll keep those around.

Initially, the :file:`project.clj` is almost empty:

.. ex:: create-project project.clj

Our first step is to fill in a few details, including the dependency on Lacinia:

.. ex:: add-lacinia-dep project.clj
   :emphasize-lines: 2,3,7

Lacinia has just a few dependencies of its own:

.. graphviz::

    digraph {
      graph [rankdir=TD];
      "clojure-game-geek-1216" [label="clojure-game-geek/
      clojure-game-geek
      0.1.0-SNAPSHOT",shape=doubleoctagon];
      "clojure-1217" [label="org.clojure/
      clojure
      1.8.0"];
      "lacinia-1218" [label="com.walmartlabs/
      lacinia
      0.21.0"];
      "clj-antlr-1219" [label="clj-antlr
      0.2.4"];
      "antlr4-1220" [label="org.antlr/
      antlr4
      4.5.3"];
      "antlr4-runtime-1221" [label="org.antlr/
      antlr4-runtime
      4.5.3"];
      "clojure-future-spec-1222" [label="clojure-future-spec
      1.9.0-alpha17"];
      "ordered-1223" [label="org.flatland/
      ordered
      1.5.6"];
      "useful-1224" [label="org.flatland/
      useful
      0.9.0"];
      "clojure-game-geek-1216" -> "clojure-1217";
      "clojure-game-geek-1216" -> "lacinia-1218";
      "lacinia-1218" -> "clj-antlr-1219";
      "lacinia-1218" -> "clojure-future-spec-1222";
      "lacinia-1218" -> "ordered-1223";
      "clj-antlr-1219" -> "antlr4-1220";
      "clj-antlr-1219" -> "antlr4-runtime-1221";
      "ordered-1223" -> "useful-1224";
    }

`Antlr <http://www.antlr.org/>`_ is used to parse GraphQL queries and schemas.
``org.flatland/ordered`` provides the ordered map type, used to ensure that response
keys and values are in the client-specified order, as per the GraphQL spec.
