Contributing to Lacinia
=======================

We hope to build a community around Lacinia and extensions and enhancements to it.

Contributor License Agreements
------------------------------

We are working on a process for accepting contributor license agreements.
Once we have a signed CLA, we will gladly investigate pull requests.

Issue Tracking
--------------

We expect the built-in GitHub issue tracker to be sufficient.

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

We may reject patches that arrive without tests.

Backwards Compatibility
-----------------------

We respect backwards compatibility.
We hope that anyone who writes a program using Lacinia will be free from upgrade headaches, even
as Lacinia gains more features.

We have, so far, expressly `not` documented what a compiled schema or parsed query looks like, so
that we can be free to be fluid in make improvements.
