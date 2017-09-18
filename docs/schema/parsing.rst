GraphQL IDL Schema Parsing
==========================

.. important::
   The GraphQL Interface Definition Language is not yet a formal specification
   and is still under development. As such, this part of Lacinia will continue
   to evolve to keep up with new developments, and it's possible that breaking
   changes will occur.

.. sidebar:: GraphQL IDL

  See the `RFC PR <https://github.com/facebook/graphql/pull/90>`_ for details
  on the GraphQL Interface Definition Language.

As noted in the :doc:`overview <../overview>`, Lacinia schemas are represented as
Clojure data. However, Lacinia also contains a facility to transform schemas
written in the GraphQL Interface Definition Language into the form usable by Lacinia.
This is exposed by the function ``com.walmartlabs.lacinia.parser.schema/parse-schema``.

The Lacinia schema definition includes things which are not available in the IDL, such as
resolvers, subscription streamers, custom scalar parsers/serializers and documentation.
To add these, ``parse-schema`` has two arguments: a string containing the IDL
schema definition, and a map of resolvers, streamers, scalar functions and documentation
to attach to the schema:

.. code-block:: clojure

  {:resolvers {:field-name resolver-fn}
   :streamers {:field-name stream-fn}
   :scalars {:scalar-name {:parse parse-spec
                           :serialize serialize-spec}}
   :documentation {:type-name doc-str
                   :type-name/field-name doc-str
                   :type-name.field-name/arg-name doc-str}}

Example
-------

.. literalinclude:: ../_examples/sample_schema.txt
   :caption: *schema.txt*
   :name: schema

.. code-block:: clojure

  (parse-schema (slurp (clojure.java.io/resource "schema.txt"))
                {:resolvers {:Query {:find_all_in_episode :find-all-in-episode}
                                     :Mutation {:add_character :add-character}}
                 :documentation {:Character "A Star Wars character"
                                 :Character/name "Character name"
                                 :Query/find_all_in_episode "Find all characters in the given episode"
                                 :Query.find_all_in_episode/episode "Episode for which to find characters."}})

.. literalinclude:: ../_examples/parsed_sample_schema.edn
   :caption: *Return value of parse-schema*
   :name: return-value
   :language: clojure

.. note::
  The GraphQL IDL represents queries, mutations and subscriptions as types,
  and Lacinia represents them as fields on ``:queries``, ``:mutations``, and
  ``:subscriptions``. The Lacinia schema will retain the original types under
  ``:objects``, which may be unused unless other parts of your schema definition
  reference those types.
