(ns com.walmartlabs.test-reporting
  "An extension for clojure.test that allows additional context to be output only
when a test failure or error occurs."
  (:require
    [clojure.test :refer [*report-counters* *initial-report-counters*]]
    [clojure.pprint :refer [write]]))

;; This could easily be spun off as its own tiny testing library.

(def ^:dynamic *reporting-context*
  "Contains a map of additional information to be printed in the report
  when a test failure or error is observed."
  nil)

(def reported?
  "Used to prevent multiple reports of the context. This flag is set
  to true when [[*reporting-context*]] is bound with new data, and reset to
  false when the context is printed out."
  (atom false))

(defn snapshot-counters
  []
  (select-keys @*report-counters* [:fail :errors]))

(defn report-context
  []
  (when *reporting-context*
    (println " context:\n"
             (write *reporting-context* :stream nil :pretty true))))

(defmacro report
  "Establishes a context in which certain data is printed
  when the form (tests using the `is` macro)
  identify test failures or exceptions.

  This adds keys to the [[*reporting-context*]].
  After executing the forms, a check is made to see if
  the number of errors or failures changed; if so
  then the reporting context is pretty-printed to *out*.

  The data maybe a symbol: The unevaluated symbol becomes
  the key, and the evaluated symbol is the value.

  Alternately, data may be a map, which is merged into the context.
  In this form, keys must be quoted if symbols.

      (report request
          (is ...))

  is the same as:

      (report {'request request}
         (is ...))

  A final alternative is to report a vector; each of the symbols
  is quoted.

      (report [request response] ...)

  is the same as:

      (report {'request request 'response response} ...)

  Nested usages of reporting is allowed; the context is
  reported at the _end_ of the block and a reasonable attempt
  is made to prevent the context from being printed multiple times."
  [data & forms]
  (cond

    (symbol? data)
    `(report {(quote ~data) ~data} ~@forms)

    (vector? data)
    `(report ~(into {} (map #(vector (list 'quote %) %)) data) ~@forms)

    (not (map? data))
    (throw (ex-info "com.walmartlabs.reporting/report - data must be a symbol, vector or a map"
                    {:data data :forms forms}))

    :else
    `(binding [*report-counters* (or *report-counters*
                                     (ref *initial-report-counters*))
               *reporting-context* (merge *reporting-context* ~data)]
       (let [counters# (snapshot-counters)]
         (try
           ;; New values have been bound into *reporting-context* that need
           ;; to be reported on a failure or error.
           (reset! reported? false)
           ~@forms
           (finally
             (when (and (not @reported?)
                        (not= counters# (snapshot-counters)))
               ;; Don't do further reporting while unwinding, and don't
               ;; try to report the context a second time if there's an exception
               ;; the first time. It is expected that some of the context values
               ;; will be quite large, so we want to ensure that they are not
               ;; pretty-printed multiple times.
               (reset! reported? true)
               ;; The point here is to call report-context at the deepest level,
               ;; so all the keys can appear together. Looks better (due to
               ;; indentation rules). However, it would be a lot simpler to just
               ;; let each reporting block track its own keys/values in a local
               ;; symbol.
               (report-context))))))))
