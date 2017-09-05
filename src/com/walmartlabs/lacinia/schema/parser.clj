(ns com.walmartlabs.lacinia.schema.parser
  (:require [clj-antlr.core :as antlr.core]
            [clj-antlr.proto :as antlr.proto]
            [clojure.java.io :as io]
            [clj-antlr.common :as antlr.common]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.internal-utils
             :refer [cond-let update? q map-vals filter-vals
                     with-exception-context throw-exception to-message
                     keepv as-keyword]])
  (:import (org.antlr.v4.runtime.tree ParseTree TerminalNode)
           (org.antlr.v4.runtime Parser ParserRuleContext Token)
           (clj_antlr ParseError)
           (clojure.lang ExceptionInfo)))

(def ^:private grammar
  (antlr.core/parser (slurp (io/resource "com/walmartlabs/lacinia/schema.g4"))))

(def ^:private ignored-terminals
  "Textual fragments which are to be immediately discarded as they have no
  relevance to a formed parse tree."
  #{"'{'" "'}'" "'('" "')'" "'['" "']'" "'...'" "'fragment'" "'on'"
    "':'" "'='" "'$'" "'!'" "\"" "'@'"})

(defn ^:private ignored-terminal?
  [token-name]
  (some? (some ignored-terminals #{token-name})))

(defn ^:private token-name
  "Returns the rule name of a terminal node, eg. :alias or :field."
  [^TerminalNode ctx ^Parser parser]
  (let [sym (.getSymbol ctx)
        idx (.getType sym)]
    (when-not (neg? idx)
      (aget (.getTokenNames parser) idx))))

(defn ^:private attach-location-as-meta
  "Attaches location information {:line ... :column ...} as metadata to the
  sexp."
  [^ParseTree t sexp]
  (when sexp
    (let [^Token token (.getStart ^ParserRuleContext t)]
      (with-meta
        sexp
        {:line (.getLine token)
         :column (.getCharPositionInLine token)}))))

(defn ^:private traverse
  [^ParseTree t ^Parser p]
  (if (instance? ParserRuleContext t)
    (let [node (cons (->> (.getRuleIndex ^ParserRuleContext t)
                          (antlr.common/parser-rule-name p)
                          antlr.common/fast-keyword)
                     (keepv (comp
                             #(attach-location-as-meta t %)
                             #(traverse % p))
                            (antlr.common/children t)))]
      (if-let [e (.exception ^ParserRuleContext t)]
        (with-meta (list :clj-antlr/error node)
          {:error (antlr.common/recognition-exception->map e)})
        node))

    (let [token-name* (token-name t p)]
      (when-not (ignored-terminal? token-name*)
        (list (keyword (str/lower-case token-name*))
              (.getText t))))))

(defn ^:private antlr-parse
  [schema-string]
  (let [{:keys [tree parser]} (antlr.proto/parse grammar nil schema-string)]
    (traverse tree parser)))