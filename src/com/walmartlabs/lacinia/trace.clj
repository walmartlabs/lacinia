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

(ns com.walmartlabs.lacinia.trace
  "Light-weight, asynchronous logging built around tap>.

  Follows the same pattern as asserts: tracing may be compiled or not; if compiled,
  it may be enabled, or not.  Finally, you must add a tap (typically,
  clojure.pprint/pprint) to receive the maps that trace may produce."
  (:require [io.aviso.exception :refer [demangle]]
            [clojure.string :as string]))

(def ^:dynamic *compile-trace*
  "If false (the default), calls to trace evaluate to nil."
  false)

(def ^:dynamic *enable-trace*
  "If false (the default is true) then compiled calls to trace
  are a no-op."
  true)

(defn set-compile-trace!
  [value]
  (alter-var-root #'*compile-trace* (constantly value)))

(defn set-enable-trace!
  [value]
  (alter-var-root #'*enable-trace* (constantly value)))

(defn ^:private extract-fn-name
  [class-name]
  (let [[_ & raw-function-ids] (string/split class-name #"\$")]
    (->> raw-function-ids
      (map #(string/replace % #"__\d+" ""))
      (map demangle)
      (string/join "/"))))

(defn ^:no-doc extract-in
  [trace-ns]
  (let [ns-string (-> trace-ns ns-name name)
        stack-frame (-> (Thread/currentThread)
                        .getStackTrace
                        (nth 3))
        fn-name (extract-fn-name (.getClassName ^StackTraceElement stack-frame))]
    (symbol ns-string fn-name)))

(defmacro trace
  "Calls to trace generate a map that is passed to `tap>`.

  The map includes keys:
  :in - a symbol of the namespace and function
  :line - the line number of the trace invocation (if available)
  :thread - the string name of the current thread

  Additional keys and values may be supplied.

  trace may expand to nil, if compilation is disabled.

  Any invocation of trace evaluates to nil."
  [& kvs]
  (assert (even? (count kvs))
    "pass key/value pairs")
  (when *compile-trace*
    (let [{:keys [line]} (meta &form)
          trace-ns *ns*]
      `(when *enable-trace*
         ;; Maps are expected to be small; array-map ensures that they keys are in insertion order.
         (tap> (array-map
                 :in (extract-in ~trace-ns)
                 ~@(when line [:line line])
                 :thread (.getName (Thread/currentThread))
                 ~@kvs))
         nil))))

