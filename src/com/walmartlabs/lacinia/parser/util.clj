(ns com.walmartlabs.lacinia.parser.util
  (:require [clojure.string :as str]))

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
