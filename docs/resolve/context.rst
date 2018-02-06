Application Context
===================

The application context passed to your field resolvers is normally set by the initial call to
``com.walmartlabs.lacina/execute-query``.
Lacinia uses the context for its own book-keeping (the keys it places into the map are namespaced to
avoid collisions) but otherwise the same map is passed to all field resolvers.

In specific cases, it is useful to allow a field resolver to modify the application context, with the change
exposed just to the fields nested below, but to any depth.

For example, in this query::

  {
    products(search: "fuzzy") {
      category {
        name
        product {
          upc
          name
          highlighted_name
        }
     }
  }

Here, the search term is provided to the ``products`` field, but is again needed by the ``highlighted_name``
field, to highlight the parts of the name that match the search term.

The resolver for the ``product`` field can communicate this "down tree" to the resolver for the ``highlighted_name`` field:

.. literalinclude:: /_examples/mutable-context.edn
   :language: clojure

This new key, ``::search-term``, is only present in the context below ``products`` field.

When using `lacinia-pedestal <https://github.com/walmartlabs/lacinia-pedestal>`_, the default behavior is to capture the Ring request map and supply it
in the application context under the ``:request`` key.
