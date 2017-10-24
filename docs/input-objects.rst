Input Objects
=============

.. sidebar:: GraphQL Spec

   Read about :spec:`input objects <Input-Objects>`.

In some cases, it is desirable for a query to include arguments with more complex data than a single value.
A typical example would be passing a bundle of values as part of a
:doc:`mutation <mutations>` operation (rather than an unmanageable number of individual field arguments).

Input objects are defined like ordinary objects, with a number of restrictions:

- Field types are limited to scalars, enums, and input objects (or list and non-null wrappers around
  those types)
- There are no field resolvers for input objects; these are values passed in their entirety from
  the client to the server
- Input objects do not implement interfaces

Input objects are defined as keys of the top-level ``:input-objects`` key in the schema.

