Clojure 1.9
===========

Lacinia targets Clojure 1.9, and makes specific use of ``clojure.spec``.

To use Lacinia with Clojure 1.8, modify your :file:`project.clj` to include ``clojure-future-spec``::

    :dependencies [[org.clojure/clojure "1.8.0"]
                   [com.walmartlabs/lacinia "x.y.z"]
                   [clojure-future-spec "1.9.0-beta4"]
                   ...]

