GraphQL SDL Parsing
===================

.. sidebar:: GraphQL Spec

   Read about :spec:`the Schema Definition Language <Schema>`.

As noted in the :doc:`overview <../overview>`, Lacinia schemas are represented as
Clojure data. However, Lacinia also contains a facility to transform schemas
written in the GraphQL Schema Definition Language into the form usable by Lacinia.
This is exposed by the function :api:`parser.schema/parse-schema`.

The Lacinia schema definition includes things which are not available in the SDL, such as
resolvers, subscription streamers, custom scalar parsers/serializers and documentation.
To add these, ``parse-schema`` has two arguments: a string containing the SDL
schema definition, and a map of resolvers, streamers, scalar functions and documentation
to attach to the schema:

.. code-block:: clojure

  {:resolvers {:field-name resolver-fn}
   :streamers {:field-name stream-fn}
   :scalars {:scalar-name {:parse parse-spec
                           :serialize serialize-spec}}
   :documentation {:type-name doc-str
                   :type-name/field-name doc-str
                   :type-name/field-name.arg-name doc-str}}

Example
-------

.. literalinclude:: /_examples/sample_schema.txt
   :caption: *schema.txt*
   :name: schema

.. code-block:: clojure

  (parse-schema (slurp (clojure.java.io/resource "schema.txt"))
                {:resolvers {:Query {:find_all_in_episode :find-all-in-episode}
                                     :Mutation {:add_character :add-character}}
                 :documentation {:Character "A Star Wars character"
                                 :Character/name "Character name"
                                 :Query/find_all_in_episode "Find all characters in the given episode"
                                 :Query/find_all_in_episode.episode "Episode for which to find characters."}})

.. literalinclude:: /_examples/parsed_sample_schema.edn
   :caption: *Return value of parse-schema*
   :name: return-value
   :language: clojure

The ``:documentation`` key uses a naming convention on the keys which become paths into the Lacinia input schema.
``:Character/name`` applies to the ``name`` field of the ``Character`` object.
``:Query/find_all_in_episode.episode`` applies to the ``episode`` argument, inside the ``find_all_in_episode`` field
of the ``Query`` object.

.. tip::

   Attaching documentation this way is less necessary since release 0.29.0, which added support for
   embedded :spec:`schema documentation <Descriptions>`.

   Alternately, the documentation map can be parsed from a Markdown file using
   :api:`parser.docs/parse-docs`.

The same key structure can be used to document input objects and interfaces.

Unions may be documented, but do not contain fields.

Enums may be documented, as well as Enum values (e.g., ``:Episode/JEDI``).

As is normal with Schema Definition Language, the available queries, mutations, and subscriptions (not shown in this example)
are defined on ordinary schema objects, and the ``schema`` element identifies which objects are used for
which purposes.

The ``:roots`` map inside the Lacinia schema is equivalent to the ``schema`` element in the SDL.

.. warning::

   :spec:`Schema extensions <Schema-Extension>` are defined in the GraphQL specification, but not yet implemented.
