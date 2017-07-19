Nested Selections
=================

To review: executing a GraphQL query is highly recursive, where a field resolver will be responsible for
obtaining data from an external resource or data store, and nested fields will refine the root data
and make selections.

Consider a simple GraphQL query::

   {
     hero
     {
       name
       friends { name }
       ... on human {
         home_planet
       }
     }
   }

The query can be visualized as a tree of selections:

.. graphviz::

   digraph {


    subgraph cluster_query {

      label="Selections"

      hero
      name
      friends
      fname [label="name"]
      humanfrag[label="... on human"]
      planet[label="home_planet"]

      hero -> {name; humanfrag, friends}
      friends -> fname
      humanfrag -> planet
    }

    subgraph cluster_schema {

      label="Schema"

      node[shape=rectangle]
      sqr [label="QueryRoot"]
      shuman [label="human"]

      schar [label="character"]

      node[shape=ellipse]
      shero [label="hero"]

      sfriends [label="friends"]
      sappears_in [label="appears_in"]

      sname [label="name"]
      shriends [label="friends"]
      shappears_in [label="appears_in"]
      shname[label="name"]
      splanet [label="home_planet"]

      sqr -> shero
      schar -> { sname, sfriends, sappears_in}
      shuman -> { shname, shriends, shappears_in, splanet }

    }

      edge[style=dashed]

      name -> sname
      hero -> shero
      planet -> splanet
      friends -> sfriends
      fname -> sname

   }

Nodes in the selections tree relate to fields in the schema.
Remember that the type of query ``hero`` is the interface type ``character`` (and so can be either a ``human``
or a ``droid``).

In execution order, resolution occurs top to bottom, so the ``hero`` selection occurs
first, then (potentially :doc:`in parallel <async>`) ``friends``, ``home_planet``, and (hero) ``name``.
These last two are leaf nodes, because they are scalar values.
The list of ``characters`` (from the ``friends`` field) then has its ``name`` field selected.
The result map then constructs from bottom to top.

Previewing Selections
---------------------

.. warning::

   This functionality is new and somewhat experimental, and so subject to change. Feedback is encouraged!

A field resolver can "preview" what fields will be selected below it in the selections tree.
This is a tool frequently used to optimize data retrieval operations.

As an example, lets assume a starting configuration where the ``hero`` field resolver fetches just the
basic data for a hero (``id``, ``name``, ``home_planet``, etc.) and the
``friends`` resolver does a second query against the database to fetch the list of friends.

That's two database queries. Perhaps we can optimize things by getting rid of the
``friends`` resolver, and doing a join to fetch the hero and friends at the same time.
The ``hero`` resolver can just ensures there's a ``:friends`` key in the map (with the fetched friend values), and
the default field resolver for the ``friends`` field will access it.

That's simpler, but costly when ``friends`` is not part of the query.

The function ``com.walmartlabs.lacinia.executor/selects-field?`` can help here:

.. literalinclude:: ../_examples/selects-field.edn
   :language: clojure

Here, inside the application context (provided to the ``resolve-hero`` function)
is information about the selections, and ``selects-field?``
can determine if a particular field appears `anywhere` below ``hero`` in the selection tree.

``selects-field?`` identifies fields even inside nested or named fragments,
``(executor/selects-field? context :human/home_planet)`` would return true.

It is also possible to get `all` the fields that will be selected, using ``selections-seq``.
This a lazy, breadth-first navigation of all fields in the selection tree.

In the sequence of field names, any fragments are collapsed into their containing fields.

This level of detail may be insufficient, in which case the function ``selections-tree``
can be used.

This function builds a recursive structure that identifies the entire tree structure.
For the above query, it would return the following structure:

.. literalinclude:: ../_examples/selections-tree.edn
   :language: clojure

This shows, for example, that ``:character/name`` is used in two different ways (inside the
``hero`` query itself, and within the ``friends`` field).

For fields with arguments, an ``:args`` key is present, with the exact values which will be
supplied to the the nested field's resolver.

Fields with neither arguments nor sub-selections are represented as nil.


