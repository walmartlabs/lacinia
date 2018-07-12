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

(ns ^:no-doc com.walmartlabs.lacinia.parser.common
  (:require [clj-antlr.proto :as antlr.proto]
            [clj-antlr.common :as antlr.common]
            [clojure.string :as str]
            [com.walmartlabs.lacinia.internal-utils
             :refer [keepv]])
  (:import (org.antlr.v4.runtime.tree ParseTree TerminalNode)
           (org.antlr.v4.runtime Parser ParserRuleContext Token)
           (clj_antlr ParseError)))

(defn ^:private unescape-ascii
  [^String escaped-sequence]
  (case escaped-sequence
    "b" "\b"
    "f" "\f"
    "n" "\n"
    "r" "\r"
    "t" "\t"
    escaped-sequence))

(defn ^:private unescape-unicode
  [^String hex-digits]
  (-> hex-digits
      (Integer/parseInt 16)
      (Character/toChars)
      (String.)))

(defn stringvalue->String
  "Transform an ANTLR string value into a Clojure string."
  [^String v]
  (-> v
      ;; Because of how parsing works, the string literal includes the enclosing quotes
      (subs 1 (dec (.length v)))
      (str/replace #"\\([\\\"\/bfnrt])" #(unescape-ascii (second %)))
      (str/replace #"\\u([A-Fa-f0-9]{4})" #(unescape-unicode (second %)))))

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
        ;; Antlr numbers lines from 1, but columns from 0
        {:line (.getLine token)
         :column (-> token .getCharPositionInLine inc)}))))

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

(defn antlr-parse
  [grammar schema-string]
  (let [{:keys [tree parser]} (antlr.proto/parse grammar nil schema-string)]
    (traverse tree parser)))

(defn parse-failures
  [^ParseError e]
  (let [errors (deref e)]
    (map (fn [{:keys [line column message]}]
           {:locations [{:line line
                         :column column}]
            :message message})
         errors)))

