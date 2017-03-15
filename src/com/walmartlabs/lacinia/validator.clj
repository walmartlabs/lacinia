(ns com.walmartlabs.lacinia.validator
  "Implements query validation (eg. typechecking of vars, fragment types, etc.),
  but also a place where complexity analysis will occur."
  (:require [com.walmartlabs.lacinia.validation.scalar-leafs :refer [scalar-leafs]]
            [com.walmartlabs.lacinia.validation.fragment-names :refer [known-fragment-names]]
            [com.walmartlabs.lacinia.validation.no-unused-fragments
             :refer [no-unused-fragments]]))


;;-------------------------------------------------------------------------------
;; ## Rules

(def ^:private default-rules
  [scalar-leafs

   ;; fragments
   known-fragment-names
   no-unused-fragments])

;; —————————————————————————————————————————————————————————————————————————————
;; ## Public API

(defn validate
  "Performs validation of the query against
  a set of default rules.
  Returns empty sequence if no errors."
  [compiled-schema query-map opts]
  (mapcat #(% compiled-schema query-map)
          default-rules))
