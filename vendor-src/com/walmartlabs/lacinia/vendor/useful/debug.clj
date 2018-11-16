(ns com.walmartlabs.lacinia.vendor.useful.debug)

;; leave out of ns decl so we can load with classlojure.io/resource-forms
(require '[clojure.pprint :as p])
(require '[clojure.stacktrace :as s])

(letfn [(interrogate-form [list-head form]
          `(let [display# (fn [val#]
                            (let [form# (with-out-str
                                          (clojure.pprint/with-pprint-dispatch
                                            clojure.pprint/code-dispatch
                                            (clojure.pprint/pprint '~form)))
                                  val# (with-out-str (clojure.pprint/pprint val#))]
                              (~@list-head
                               (if (every? (partial > clojure.pprint/*print-miser-width*)
                                           [(count form#) (count val#)])
                                 (str (subs form# 0 (dec (count form#))) " is " val#)
                                 (str form# "--------- is ---------\n" val#)))))]
             (try (doto ~form display#)
                  (catch Throwable t#
                    (display# {:thrown t#
                               :trace (with-out-str
                                        (clojure.stacktrace/print-cause-trace t#))})
                    (throw t#)))))]

  (defmacro ?
    "A useful debugging tool when you can't figure out what's going on:
  wrap a form with ?, and the form will be printed alongside
  its result. The result will still be passed along."
    [val]
    (interrogate-form `(#(do (print %) (flush))) val))

  (defmacro ^{:dont-test "Complicated to test, and should work if ? does"}
    ?!
    ([val] `(?! "/tmp/spit" ~val))
    ([file val]
       (interrogate-form `(#(spit ~file % :append true)) val))))
