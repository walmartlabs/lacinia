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

.. sidebar:: CLA

   When submitting a PR, you will need to sign the Contributor License Agreement (CLA) before the
   PR can be *reviewed or merged*. The CLA is quite straightforward, and simply assigns
   copyright and patent rights to Walmart for any contributed changes to Lacinia.

After at least two core committers provide the traditional feedback LGTM (looks good to me), we'll merge to master.

It is the submitter's responsibility to keep a PR up to date when it has conflicts with master.

Please do not change the version number in :file:`VERSION.txt`; the core committers will handle version number changes.
Generally, we advance the version number immediately after a release.

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

Keep as much as possible private; the more public API there is, the more there is
to support release on release.

Occasionally it is not reasonable to refactor a common implementation function
out of a public namespace to a private namespace, in order to share
the function between namespaces.
In that case, add the ``:no-doc`` metadata.
This will prevent that var from appearing in the generated API documentation,
and signify that the function is intended to be private (and therefore,
subject to change without notice).

Where possible, apply ``:added`` metadata on newly created namespaces or newly added
functions (or vars).

When a new namespace is introduced, only the namespace needs the ``:added`` metadata,
not the individual functions (or vars).

We indent with spaces, and follow default indentation patterns.

We value documentation.
Lacinia docstrings are formatted with Markdown.
Tests can also be great documentation.

Tests
-----

We are test driven.
We expect patches to include tests.

We may reject patches or pull requests that arrive without tests.

Documentation
-------------

Patches that change behavior and invalidate existing documentation will be rejected.
Such patches should also update the documentation.

Ideally, patches that introduce new functionality will also include documentation changes.

Documentation is generated using `Sphinx <http://www.sphinx-doc.org/en/stable/contents.html>`_,
which is not difficult to set up, but does require Python.

Backwards Compatibility
-----------------------

We respect backwards compatibility.

We make it a top priority to ensure that anyone who writes an application using Lacinia will be free from upgrade headaches, even
as Lacinia gains more features.

We have, so far, expressly `not` documented the internal structure of compiled schema or parsed query, so
that we can be free to be fluid in making future improvements.
