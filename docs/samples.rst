Sample Projects
===============

`boardgamegeek-graphql-proxy <https://github.com/hlship/boardgamegeek-graphql-proxy>`_
  Howard Lewis Ship created this simple proxy to expose part of the
  `BoardGameGeek <https://boardgamegeek.com/>`_ database as GraphQL, using Lacinia.

  It was used for examples in his
  Clojure/West 2017 talk: `Power to the (Mobile) People: Clojure and GraphQL <http://2017.clojurewest.org/clojure-graphql/>`_.
  
`leaderboard-api <https://github.com/jborden/leaderboard-api>`_
  A simple API to track details about games and high scores.  
  Built on top of Compojure and PostgreSQL.
  See `this blog post <https://jborden.github.io/2017/05/15/using-lacinia>`_ by the author.

`open-bank-mark <https://github.com/openweb-nl/kafka-graphql-examples>`_
  This project consists of multiple components creating a bank simulation.

  The `graphql-endpoint <https://github.com/openweb-nl/kafka-graphql-examples/tree/master/graphql-endpoint>`_
  component consists of three services that all consume from Kafka.
  It's mainly working with subscriptions where a command is put to Kafka and the result is returned.
  It is also possible to query transactions, using a derived view.
  PostgreSQL is used to store user accounts for logging in, and to store all the transactions.
  The `test module <https://github.com/openweb-nl/kafka-graphql-examples/blob/master/test/src/nl/openweb/test/generator.clj>`_
  Contains a generator to load test the subscriptions and can be used as inspiration to do similar testing.

  Also part of the project is a `frontend <https://github.com/openweb-nl/open-bank-mark/tree/master/frontend>`_
  using `re-graph <https://github.com/oliyh/re-graph>`_.
  Users can login, transfer money, and get an overview of all the bank accounts.
  
`Fullstack Learning Project <https://promesante.github.io/2019/08/14/clojure_graphql_fullstack_learning_project_part_1.html>`_ 
  A port of `The Fullstack Tutorial for GraphQL <https://www.howtographql.com/>`_, ported to Clojure and Lacinia.
  
`Hacker News GraphQL <https://www.giovanialtelino.com/project/hacker-news-graphql/>`_
  A version of Hacker News implemented using GraphQL and `Datomic <https://www.datomic.com/>`_ on the backend,
  and `re-frame <https://day8.github.io/re-frame/re-frame/>`_ on the front end.
  
`Lacinia LDAP backend <https://github.com/matteoredaelli/lacinia-backend-ldap/>`_
  A sample library for querying a LDAP/Active directory using GraphQL

`Lacinia Qliksense backend <https://github.com/matteoredaelli/lacinia-backend-ldap/>`_
  A sample library for querying a Qliksense server (Repository API) using GraphQL
