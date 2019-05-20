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

`open-bank-mark <https://github.com/openweb-nl/open-bank-mark>`_
  This project consists of multiple components creating a bank simulation.

  The `graphql-endpoint <https://github.com/openweb-nl/open-bank-mark/tree/master/graphql-endpoint>`_
  component consists of three services that all consume from Kafka.
  It's mainly working with subscriptions where a command is put to Kafka and the result is returned.
  It is also possible to query for or subscribe to transactions.
  PostgreSQL is used to store user accounts for logging in, and to store all the transactions.

  Also part of the project is a `frontend <https://github.com/openweb-nl/open-bank-mark/tree/master/frontend>`_
  using `re-graph <https://github.com/oliyh/re-graph>`_.
  Users can login, transfer money, and get an overview of all the bank accounts.