Clojure 1.9
===========

Lacinia is currently designed for Clojure 1.8.

Lacinia makes use of features of ``clojure.spec``, introduced in Clojure 1.9.

Lacinia uses `clojure-future-spec <https://github.com/tonsky/clojure-future-spec>`_ to enable
use of ``clojure.spec`` features with Clojure 1.8.

To use Lacinia with Clojure 1.9, modify your :file:`project.clj` to exclude ``clojure-future-spec``::

    :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                   [com.walmartlabs/lacinia "x.y.z" :exclusions [clojure-future-spec]
                   ...]

We will revisit Clojure 1.9 compatibility once 1.9, including ``clojure.spec``, is stable.
