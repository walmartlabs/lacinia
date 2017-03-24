Clojure Version
===============

Lacinia is currently designed for Clojure 1.8.

Lacinia makes use of features of ``clojure.spec``, introduced in Clojure 1.9.
Clojure 1.9 is still in an alpha state, 10 months after its initial release, with no indication
when a stable release will arrive.

Lacinia uses `clojure-future-spec <https://github.com/tonsky/clojure-future-spec>`_ to enable
use of ``clojure.spec`` features with Clojure 1.8.

To use Lacinia with Clojure 1.9, modify your :file:`project.clj` to exclude ``clojure-future-spec``::

    :dependencies [[org.clojure/clojure "1.9.0-alpha14"]
                   [com.walmartlabs/lacinia "x.y.z" :exclusions [clojure-future-spec]
                   ...]


