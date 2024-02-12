(ns com.walmartlabs.lacinia.parser.antlr
  "Common functions for building and using parsers.
  Excerpted from clj-antlr.common"
  (:import (java.util.concurrent ConcurrentHashMap)
           (org.antlr.v4.runtime ANTLRErrorListener
                                 Parser
                                 RecognitionException)
           (org.antlr.v4.runtime.tree Tree)))

(def ^ConcurrentHashMap fast-keyword-cache
  "A map of strings to keywords."
  (ConcurrentHashMap. 1024))

(defn fast-keyword
  "Like (keyword str), but faster."
  [s]
  (or (.get fast-keyword-cache s)
      (let [k (keyword s)]
        (if (< 1024 (.size fast-keyword-cache))
          k
          (do
            (.put fast-keyword-cache s k)
            k)))))

(defn child-count
  "How many children does a node have?"
  [^Tree node]
  (.getChildCount node))

(defn children
  "Returns the children of a RuleNode."
  [^Tree node]
  (map #(.getChild node %)
       (range (child-count node))))

(defn parser-rule-name
  "Given a parser and an integer rule index, returns the string name of that
  rule. Negative indexes map to nil."
  [^Parser parser ^long index]
  (when-not (neg? index)
    (aget (.getRuleNames parser) index)))

(defn recognition-exception->map
  "Converts a RecognitionException to a nice readable map."
  [^RecognitionException e]
  {:rule     (.getCtx e)
   :state    (.getOffendingState e)
   :expected (try (.getExpectedTokens e)
                  (catch IllegalArgumentException _
                    ; I think ANTLR throws here for
                    ; tokenizer errors.
                    nil))
   :token    (.getOffendingToken e)})

(defn error-listener
  "A stateful error listener which accretes parse errors in a deref-able
  structure. Deref returns nil if there are no errors; else a sequence of
  heterogenous maps, depending on what debugging information is available."
  []
  (let [errors (atom [])]
    (reify
      clojure.lang.IDeref
      (deref [this] (seq (deref errors)))

      ANTLRErrorListener
      (reportAmbiguity [this
                        parser
                        dfa
                        start-index
                        stop-idex
                        exact
                        ambig-alts
                        configs]
        ; TODO
        )

      (reportAttemptingFullContext [this
                                    parser
                                    dfa
                                    start-index
                                    stop-index
                                    conflicting-alts
                                    configs])

      (reportContextSensitivity [this
                                 parser
                                 dfa
                                 start-index
                                 stop-index
                                 prediction
                                 configs])

      (syntaxError [this
                    recognizer
                    offending-symbol
                    line
                    char
                    message
                    e]
        (let [err {:symbol   offending-symbol
                   :line     line
                   :char     char
                   :message  message}
              err (if (isa? Parser recognizer)
                    (assoc err :stack (->> ^Parser recognizer
                                           .getRuleInvocationStack
                                           reverse))
                    err)
              err (if e
                    (merge err (recognition-exception->map e))
                    err)]
        (swap! errors conj err))))))
