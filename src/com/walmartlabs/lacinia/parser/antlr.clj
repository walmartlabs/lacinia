(ns com.walmartlabs.lacinia.parser.antlr
  "Mostly excerpted from clj-antlr.common"
  (:require [clojure.string :as string])
  (:import (com.walmartlabs.lacinia ParseError)
           (java.util.concurrent ConcurrentHashMap)
           (org.antlr.v4.runtime ANTLRErrorListener
                                 CharStreams CommonTokenStream Lexer Parser
                                 ParserRuleContext RecognitionException Token)
           (org.antlr.v4.runtime.tree ParseTree Tree)))

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

(defn parse-error
  "Constructs a new ParseError exception with a list of errors."
  [errors tree]
  (ParseError. errors
               tree
               (string/join "\n" (map :message errors))))

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

(defprotocol AntlrParser
  (^Lexer lexer [_ ^CharStream chars])
  (^Parser parser [_ ^TokenStream lexer])
  (^ParseTree tree [_ ^Parser parser]))

(defn parse [ap ^String input]
  (let [error-listener (error-listener)

        lexer (lexer ap (CharStreams/fromString input))
        _ (doto lexer
            (.removeErrorListeners)
            (.addErrorListener error-listener))

        token-stream (CommonTokenStream. lexer)
        parser (parser ap token-stream)
        _ (doto parser
            (.removeErrorListeners)
            (.addErrorListener error-listener))

        tree (tree ap parser)]

    (when-let [errors @error-listener]
      (throw (parse-error errors tree)))

    ;; ANTLR's error recovery can silently produce a valid parse tree even when
    ;; there are unconsumed tokens (e.g. unbalanced braces). Detect this case by
    ;; checking that every non-EOF token was consumed by the document rule.
    (.fill token-stream)
    (let [tokens (.getTokens token-stream)
          n (count tokens)
          ;; tokens is [...real-tokens... EOF]; last-real-idx is the index of the
          ;; last non-EOF token (n-2), or -1 when the input is empty.
          last-real-idx (- n 2)
          stop-token (.getStop ^ParserRuleContext tree)
          stop-idx (if stop-token (.getTokenIndex ^Token stop-token) -1)]
      (when (< stop-idx last-real-idx)
        (let [first-unconsumed (.get token-stream (inc stop-idx))
              line (.getLine ^Token first-unconsumed)
              col (inc (.getCharPositionInLine ^Token first-unconsumed))]
          (throw (parse-error [{:line line
                                :char col
                                :message (str "extraneous input '"
                                              (.getText ^Token first-unconsumed)
                                              "' expecting EOF")}]
                               tree)))))

    {:tree tree
     :parser parser}))
