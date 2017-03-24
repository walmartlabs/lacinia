Contributing to Lacinia
=======================

We hope to build a community around Lacinia and extensions and enhancements to it.

Licensing
---------

Lacinia is licensed under the terms of the `Apache Software License 2.0 <http://www.apache.org/licenses/>`_.
Any contributions made by the community, including patches, pull requests, or any content
provided in an issue, represents a transfer of intellectual property to Walmartlabs, for the sole purpose
of maintaining and improving Lacinia.

Process
-------

Our process is light: once a pull request (PR) comes in, core committers will review the
the code and provide feedback.

After at least two core committers provide the traditional feedback LGTM (looks good to me), we'll merge to master.

It is the submitter's responsibility to keep a PR up to date when it has conflicts with master.

Please close PRs that are not ready to merge, or need other refinements, and re-open when ready.

As Lacinia's community grows, we'll extend this process as needed ... but we want to keep it light.

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

We have, so far, expressly `not` documented the internal structure of compiled schema or parsed query, so
that we can be free to be fluid in making future improvements.
