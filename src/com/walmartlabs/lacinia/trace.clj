; Copyright (c) 2017-present Walmart, Inc.
;
; Licensed under the Apache License, Version 2.0 (the "License")
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
;
;     http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

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
