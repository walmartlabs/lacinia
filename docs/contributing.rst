Contributing to Lacinia
=======================

We hope to build a community around Lacinia and extensions and enhancements to it.

Licensing
---------

Lacinia is licensed under the terms of the `Apache Software License 2.0 <http://www.apache.org/licenses/>`_.
Any contributions made by the community, including patches, pull requests, or any content
provided in an issue, represents a transfer of intellectual property to Walmartlabs, for the sole purpose
of maintaining and improving Lacinia.

Issue Tracking
--------------

We currently use the
`GitHub issue tracker <https://github.com/walmartlabs/lacinia/issues>`_ for Lacinia.
We expect that to be sufficient for the meantime.

Coding Conventions
------------------

Please follow the existing code base.

We prefer ``defn ^:private`` to ``defn-``.

We indent with spaces, and follow default indentation patterns.

We value documentation.
Lacinia docstrings are formatted with Markdown.
Tests can also be great documentation.

Private is the default; only make vars public if there is a specific need.

Tests
-----

We are test driven.
We expect patches to include tests.

We may reject patches or pull requests that arrive without tests.

Backwards Compatibility
-----------------------

We respect backwards compatibility.

We make it a top priority to ensure that anyone who writes an application using Lacinia will be free from upgrade headaches, even
as Lacinia gains more features.

We have, so far, expressly `not` documented what the internal structure of compiled schema or parsed query, so
that we can be free to be fluid in making future improvements.
