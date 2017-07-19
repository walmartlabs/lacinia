Examples
========

Formatting a Date
-----------------

This is a common scenario: you have a Date or Timestamp value and it needs to be
in a specific format in the result map.

In this example, the field resolver will extract the key from the
container's resolved value, and format it:

.. code-block:: clojure

    (fn [context args resolved-value]
        (->> resolved-value
             :updated-at
             (format "%tm-%<td-%<tY")))

.. note::

    More examples forthcoming.
