Creating the Project
====================

In this first step of the tutorial, we'll create the initial empty project, and set up the
initial dependencies.

Let's get started.

The `clj` command is used to start Clojure projects, but it's a bit of swiss-army knife; it can
also be used to launch arbitrary Clojure tools.  Importantly, `clj` also understands dependencies
and repositories, so it will download any libraries, as they are needed.

We're going to install a Clojure tool, `clj-new <https://github.com/seancorfield/clj-new>`_ ::

  > clj -Ttools install com.github.seancorfield/clj-new '{:git/tag "v1.2.399"}' :as clj-new
  Cloning: https://github.com/seancorfield/clj-new.git
  Checking out: https://github.com/seancorfield/clj-new.git at c82384e437a2dfa03b050b204dd2a2008c02a6c7
  clj-new: Installed com.github.seancorfield/clj-new v1.2.399

The first step is to create a new, empty project::

  12:51:44 ~/workspaces/github > lein new clojure-game-geek
  Generating a project called clojure-game-geek based on the 'default' template.
  The default template is intended for library projects, not applications.
  To see other templates (app, plugin, etc), try `lein help new`.

With this tools installed, we can then create a new Clojure application project::

    > clj -Tclj-new app :name  my/clojure-game-geek
    Downloading: org/apache/maven/resolver/maven-resolver-spi/1.3.3/maven-resolver-spi-1.3.3.pom from central
    Downloading: org/apache/maven/resolver/maven-resolver-transport-http/1.3.3/maven-resolver-transport-http-1.3.3.pom from central
    Downloading: org/apache/maven/maven-resolver-provider/3.6.1/maven-resolver-provider-3.6.1.pom from central
    Downloading: org/apache/maven/resolver/maven-resolver-api/1.3.3/maven-resolver-api-1.3.3.pom from central
    Downloading: org/apache/maven/resolver/maven-resolver-util/1.3.3/maven-resolver-util-1.3.3.pom from central
    Downloading: org/apache/maven/resolver/maven-resolver-connector-basic/1.3.3/maven-resolver-connector-basic-1.3.3.pom from central
    Downloading: org/apache/maven/resolver/maven-resolver-impl/1.3.3/maven-resolver-impl-1.3.3.pom from central
    Downloading: org/apache/maven/resolver/maven-resolver-transport-file/1.3.3/maven-resolver-transport-file-1.3.3.pom from central
    Downloading: org/apache/maven/maven/3.6.1/maven-3.6.1.pom from central
    Downloading: stencil/stencil/0.5.0/stencil-0.5.0.pom from clojars
    Downloading: org/clojure/core.cache/0.6.3/core.cache-0.6.3.pom from central
    Downloading: org/codehaus/plexus/plexus-utils/3.2.0/plexus-utils-3.2.0.pom from central
    Downloading: org/slf4j/jcl-over-slf4j/1.7.25/jcl-over-slf4j-1.7.25.pom from central
    Downloading: quoin/quoin/0.1.2/quoin-0.1.2.pom from clojars
    Downloading: scout/scout/0.1.0/scout-0.1.0.pom from clojars
    Downloading: org/clojure/data.priority-map/0.0.2/data.priority-map-0.0.2.pom from central
    Downloading: quoin/quoin/0.1.2/quoin-0.1.2.jar from clojars
    Downloading: scout/scout/0.1.0/scout-0.1.0.jar from clojars
    Downloading: stencil/stencil/0.5.0/stencil-0.5.0.jar from clojars
    Generating a project called clojure-game-geek based on the 'app' template.

All those downloads will only occur the first time the tool is run.  The name of the project, `my/clojure-game-geek` is used define the main namespace; feel free to change the value if you like
(but then certain paths in the tutorial will also change).

The ``clj-new`` tool creates a directory, ``clojure-game-geek`` and populates it with a good starting
point for a basic Clojure application::

    > cd clojure-game-geek
    > tree .
    .
    ├── CHANGELOG.md
    ├── LICENSE
    ├── README.md
    ├── build.clj
    ├── deps.edn
    ├── doc
    │   └── intro.md
    ├── pom.xml
    ├── resources
    ├── src
    │   └── my
    │       └── clojure_game_geek.clj
    └── test
        └── my
            └── clojure_game_geek_test.clj


This is a good point to load this new, empty project into your IDE of choice.

Initially, the :file:`project.clj` is almost empty:

.. literalinclude:: /_examples/tutorial/project-1.clj
   :caption: project.clj

Our first step is to fill in a few details, including the dependency on Lacinia:

.. literalinclude:: /_examples/tutorial/project-2.clj
   :caption: project.clj
   :emphasize-lines: 2,3,7-9

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

Like any open source project, Lacinia is evolving all the time.
In later tutorial steps, we will change these dependencies to access newly added Lacinia features.
