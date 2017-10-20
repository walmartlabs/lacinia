Directives
==========

Directives provide a way to describe additional options to the GraphQL executor.
Directives allow Lacinia to change the incoming query based on additional criteria.
For example, we can use directives to include or skip a field if certain criteria are met.

.. sidebar:: GraphQL Spec

   Read about directives :spec:`here <Language.Directives>`
   and :spec:`here <Type-System.Directives>`.

Currently Lacinia supports just the two standard directives: ``@skip`` and ``@include``, but future versions
may include more.
Custom directives are not yet supported.
