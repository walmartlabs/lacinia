(ns com.walmartlabs.lacinia.expound
  "Adds improved spec messages to Lacinia specs."
  {:since "0.26.0"}
  (:require
    [expound.alpha :refer [defmsg] :as expound]
    [com.walmartlabs.lacinia.schema :as schema]
    [clojure.spec.alpha :as s]))

(defn install-expound
  []
  ;; This doesn't seem to be working, however!
  (alter-var-root #'s/*explain-out* (constantly expound/printer)))

(defmsg ::schema/resolver-type "implement the com.walmartlabs.lacina.resolve/FieldResolver protocol")
(defmsg ::schema/wrapped-type "a wrapped type: '(list type) or '(non-null type)")
(defmsg ::schema/graphql-identifier "must be a valid GraphQL identifier: contain only letters, numbers, and underscores")

(comment
  (install-expound)

  (set! s/*explain-out* expound/printer)

  (s/explain ::schema/schema-object {:queries
                                     {[:overwatch] {}}})

  (s/explain ::schema/resolve {})

  (s/explain ::schema/deprecated 7.0)

  (s/explain ::schema/fields {'foo {}})

  (s/explain ::schema/type-name :foo-bar)

  (s/explain ::schema/type 3)
  (s/explain ::schema/type '(non-nil :String))

  (s/explain ::schema/tag 3)
  (s/explain ::schema/schema-object {:objects
                                     {:Henry
                                      {:implements ['String 'Number 3 'Object]
                                       :fields
                                       {:higgins {:type :String
                                                  :resolve map?}}}}})

  (s/explain ::schema/args {:foo {:type '[flub String]}})
  (s/explain ::schema/compile-args [])

  (s/explain ::schema/implements [])

  (s/explain ::schema/enum-value :this_is_ok)
  (s/explain ::schema/enum-value "this-and-that")
  )
