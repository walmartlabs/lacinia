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

(ns com.walmartlabs.lacinia.parser.docs
  "Parsing of a Markdown file for purposes of attaching documentation to a schema."
  {:added "0.27.0"}
  (:require
    [clojure.string :as str]
    [com.walmartlabs.lacinia.internal-utils :refer [cond-let]]))

(def ^:private header-re
  ;; Don't really care how deep the heading is
  #"\#+\s+(.*?)\s*$")

(defn ^:private combine-block
  [lines]
  (->> lines
       (str/join "\n")
       str/trim))

(defn parse-docs
  "Parses an input document.  Returns a map who keys are the keyword versions of headers, and whose values
  are the (trimmed) content immediately beneath that header.

  The result is a documentation map that can be provided to [[attach-descriptions]]."
  [input]
  (loop [lines (str/split-lines input)
         result {}
         header nil
         block []]
    (cond-let
      (nil? lines)
      (if header
        (assoc result header (combine-block block))
        result)

      :let [line (first lines)
            more-lines (next lines)
            [_ new-header] (re-matches header-re line)]

      (some? new-header)
      (recur more-lines
             (if header
               (assoc result header (combine-block block))
               result)
             (keyword new-header)
             [])

      (some? header)
      (recur more-lines result header (conj block line))

      :else
      ;; In the preamble, if any, before first header.
      (recur more-lines result header block))))
