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

(comment
  (set! s/*explain-out* expound/printer)

  (s/explain ::schema/compile-args [{:queries
                                     {[:overwatch] {}}}])

  (s/explain ::schema/resolve {})

  (s/explain ::schema/deprecated 7.0)

  (s/explain ::schema/fields {'foo {}})

  (s/explain ::schema/identifier :foo-bar)

  (schema/compile {:queries
                   {:fail {:type :String
                           :resolve map?
                           :deprecated 7.0
                           }}
                   })

  (s/explain ::schema/args {:foo {:type '[flub String]}})
  )
