Subscriptions
=============

Subscriptions are GraphQL's approach to server-side push.
The description is a bit abstract, as the specification keeps all options open on how
subscriptions are implemented.

.. sidebar:: GraphQL Spec

   Read about `subscriptions <http://facebook.github.io/graphql/#sec-Subscription>`_.

With subscriptions, a client can establish a long-lived connection to a server, and
will receive new data on the connection as it becomes available to the server.

Common examples for subscriptions are updating a conversation page as new messages are added,
updating a dashboard as interesting events about a system occur, or monitoring the progress
of some long-lived process.

.. toctree::
   :hidden:

   overview
   streamer
   resolver
   lacinia-pedestal
