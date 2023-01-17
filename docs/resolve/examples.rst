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

This example is tied to a specific key (``:updated-at``) and a specific format.
A :ref:`resolver factory <resolver-factory>` could be used to make this a more general
pattern.

Accessing a Java Instance Method
--------------------------------

In some cases, you may find yourself exposing JavaBeans as schema objects.
This fights against the grain of Lacinia, which expects schema objects to be Clojure maps.

It would be tedious to write a custom field resolver function for each and every
Java instance method that needs to be invoked.
Instead, we can use a factory function:

.. literalinclude:: /_examples/resolve-method.edn
   :language: clojure

This won't be the most efficient approach, since it has to lookup a method on each use and then
invoke that method using Java reflection, but may be suitable for light use, or as the basis
for a more efficient implementation.

.. note::

    More examples forthcoming.
