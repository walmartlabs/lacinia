clojure.spec
============

Lacinia makes use of clojure.spec; specifically, the arguments to
:api:`schema/compile` and
:api:`parser.schema/parse-schema` are *always* validated with spec.

This is useful, especially for ``compile``, and the data structure passed in for compilation is complex and
deeply nested.

However, the exceptions thrown by clojure.spec can be challenging to read.

The use of `Expound <https://github.com/bhb/expound>`_ is recommended; it does a much better job of formatting
that wealth of data for a person to read.

For example, it omits all the extraneous detail, making it much easier to find where the problem exists::

  -- Spec failed --------------------
    {:objects
     {:Henry
      {:fields
       {:higgins
        {:type ...,
         :resolve ...,
         :deprecated 7.0}}}}}
                     ^^^
  should satisfy
    true?
  or
    string?


Further, Lacinia includes an extra namespace, not loaded by default: ``com.walmartlabs.lacinia.expound``.
This namespace simply defines spec messages for some of the trickier specs defined by Lacinia.
