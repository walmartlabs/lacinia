(ns com.walmartlabs.lacinia.validator
  "Implements query validation (eg. typechecking of vars, fragment types, etc.),
  but also a place where complexity analysis will occur."
  {:no-doc true}
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
  "Performs validation of the parsed and prepared query against
  a set of default rules.

  Returns an sequence of error maps, which will be empty if there are no errors.

  Note: the 3 argument version is deprecated and will be removed in a future release."
  ([prepared-query]
   (mapcat #(% prepared-query) default-rules))
  ([_ prepared-query _]
    (validate prepared-query)))
