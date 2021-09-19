(ns ^:no-doc com.walmartlabs.lacinia.trace
  "Internal tracing utility.  Not for use by applications."
  (:require [clojure.pprint :refer [pprint]]))

(def ^:dynamic *compile-trace* false)

(def ^:dynamic *enable-trace* true)

(defn set-compile-trace!
  [value]
  (alter-var-root #'*compile-trace* (constantly value)))

(defn set-enable-trace!
  [value]
  (alter-var-root #'*enable-trace* (constantly value)))

(defmacro trace
  [& kvs]
  (assert (even? (count kvs))
          "pass key/value pairs")
  (when *compile-trace*
    (let [{:keys [line]} (meta &form)
          trace-ns  *ns*]
      `(when *enable-trace*
         ;; Oddly, you need to defer invocation of ns-name to execution
         ;; time as it will cause odd class loader exceptions if used at
         ;; macro expansion time.
         (pprint (array-map
                   :ns (-> ~trace-ns ns-name (with-meta nil))
                   ~@(when line [:line line])
                   ~@kvs))))))
