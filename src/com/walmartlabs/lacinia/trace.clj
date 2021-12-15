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

  Follows the same pattern as clojure.core/assert: When tracing is not compiled,
  the tracing macros should create no runtime overhead.

  When tracing is compiled, a check occurs to see if tracing is enabled; only then
  do the most expensive operations (e.g., identifying the function containing the
  trace call) occur, as well as the call to clojure.core/tap>."
  {:no-doc true}
  (:require [io.aviso.exception :refer [demangle]]
            [clojure.string :as string]
            [clojure.pprint :refer [pprint]]))

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
  (let [[ns-id & raw-function-ids] (string/split class-name #"\$")
        fn-name (->> raw-function-ids
                     (map #(string/replace % #"__\d+" ""))
                     (map demangle)
                     (string/join "/"))]
    (symbol (demangle ns-id) fn-name)))

(defn ^:private in-trace-ns?
  [^StackTraceElement frame]
  (string/starts-with? (.getClassName frame) "com.walmartlabs.lacinia.trace$"))

(defn ^:no-doc extract-in
  []
  (let [stack-frame (->> (Thread/currentThread)
                         .getStackTrace
                         (drop 1) ; Thread/getStackTrace
                         (drop-while in-trace-ns?)
                         first)]
    (extract-fn-name (.getClassName ^StackTraceElement stack-frame))))

(defmacro ^:no-doc emit-trace
  [trace-line & kvs]
  ;; Maps are expected to be small; array-map ensures that the keys are in insertion order.
  `(when *enable-trace*
     (tap> (array-map
             :in (extract-in)
             ~@(when trace-line [:line trace-line])
             :thread (.getName (Thread/currentThread))
             ~@kvs))
     nil))

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
    (let [{:keys [line]} (meta &form)]
      `(emit-trace ~line ~@kvs))))

(defmacro trace>
  "A version of trace that works inside -> thread expressions.  Within the
  trace body, `%` is bound to the threaded value. When compilation is disabled,
  `(trace> <form>)` is replaced by just `<form>`."
  [value & kvs]
  (assert (even? (count kvs))
          "pass key/value pairs")
  (if-not *compile-trace*
    value
    (let [{:keys [line]} (meta &form)]
      `(let [~'% ~value]
         (emit-trace ~line ~@kvs)
         ~'%))))

(defmacro trace>>
  "A version of trace that works inside ->> thread expressions.  Within the
  trace body, `%` is bound to the threaded value.  When compilation is disabled,
  `(trace>> <form>)` expands to just `<form>`."
  ;; This is tricky because the value comes at the end due to ->> so we have to
  ;; work harder (fortunately, at compile time) to separate the value expression
  ;; from the keys and values.
  [& kvs-then-value]
  (let [value (last kvs-then-value)
        kvs (butlast kvs-then-value)]
    (assert (even? (count kvs))
            "pass key/value pairs")
    (if-not *compile-trace*
      value
      (let [{:keys [line]} (meta &form)]
        `(let [~'% ~value]
           (emit-trace ~line ~@kvs)
           ~'%)))))


(defn setup-default
  "Enables tracing output with a default tap of `pprint`."
  []
  (set-compile-trace! true)
  (set-enable-trace! true)
  (add-tap pprint))
