(ns com.walmartlabs.lacinia.expound-tests
  "Tests that useful Expound messages are emitted for spec failures."
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [com.walmartlabs.test-reporting :refer [reporting]]
    com.walmartlabs.lacinia.expound
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.parser.schema :as ps]
    [expound.alpha :as expound]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]))

(use-fixtures :once
  (fn [f]
    (binding [s/*explain-out* expound/printer]
      (f))))

(defmacro expect
  [spec value & substrings]
  `(let [explain# (with-out-str
                    (s/explain ~spec ~value))]
     (reporting {:explain explain#}
       (doseq [s# ~(vec substrings)]
         (is (str/includes? explain# s#))))))

(deftest uses-messages
  (expect ::schema/resolve {}
          "fn?"
          "implement the com.walmartlabs.lacina.resolve/FieldResolver protocol"))

(deftest can-report-enum-value
  (expect ::schema/enum-value 123 "string?" "simple-symbol?" "simple-keyword?")
  (expect ::schema/enum-value "this-and-that" "must be a valid GraphQL identifier"))

(deftest sdl-function-map
  (expect ::ps/fn-map {:foo :bar :gnip {:q 'gnop}}
          "fn?" "simple-keyword?"))

(comment
  (binding [s/*explain-out* expound/printer]
    (s/explain ::ps/fn-map {:foo :bar :gnip {:q 'gnop}})))
