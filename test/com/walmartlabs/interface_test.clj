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

(ns com.walmartlabs.interface-test
  "Tests related to interface definitions and implementation in objects."
  (:refer-clojure :exclude [compile])
  (:require
    [clojure.test :refer [deftest is testing]]
    [com.walmartlabs.lacinia.schema :refer [compile]])
  (:import (clojure.lang ExceptionInfo)))

(def field-not-implemented
  '{:interfaces {:named {:fields {:first_name {:type String}
                                  :last_name {:type String}}}}
    :objects {:person {:implements [:named]
                       :fields {:last_name {:type String}}}}})

(def incompatible-field-type
  '{:interfaces {:named {:fields {:first_name {:type String}
                                  :last_name {:type String}}}}
    :objects {:person {:implements [:named]
                       :fields {:first_name {:type String}
                                :last_name {:type Int}}}}})

(def incompatible-field-multiplicity
  '{:interfaces {:named {:fields {:first_name {:type String}
                                  :last_name {:type String}}}}
    :objects {:person {:implements [:named]
                       :fields {:first_name {:type String}
                                :last_name {:type (list String)}}}}})

(def incompatible-field-nullability
  '{:interfaces {:named {:fields {:first_name {:type (non-null String)}
                                  :last_name {:type String}}}}
    :objects {:person {:implements [:named]
                       :fields {:first_name {:type String}
                                :last_name {:type String}}}}})


;; Basically checking error cases; success cases show up in other
;; tests.

(deftest object-must-implement-interface-fields
  (when-let [^Throwable t (is (thrown? ExceptionInfo
                                       (compile field-not-implemented)))]
    (is (= "Missing interface field in object definition."
           (.getMessage t)))
    (is (= {:object :person
            :field-name :first_name
            :interface-name :named}
           (ex-data t)))))

(deftest field-must-be-compatible
  (testing "field type"
    (when-let [^Throwable t (is (thrown? ExceptionInfo
                                         (compile incompatible-field-type)))]
      (is (= "Object field is not compatible with extended interface type."
             (.getMessage t)))
      (is (= {:object :person
              :interface-name :named
              :field-name :last_name}
             (ex-data t)))))

  (testing "field multiplicity"
    (when-let [^Throwable t (is (thrown? ExceptionInfo
                                         (compile incompatible-field-multiplicity)))]
      (is (= "Object field is not compatible with extended interface type."
             (.getMessage t)))
      (is (= {:object :person
              :interface-name :named
              :field-name :last_name}
             (ex-data t)))))

  (testing "field nullability"
    (when-let [^Throwable t (is (thrown? ExceptionInfo
                                         (compile incompatible-field-nullability)))]
      (is (= "Object field is not compatible with extended interface type."
             (.getMessage t)))
      (is (= {:object :person
              :interface-name :named
              :field-name :first_name}
             (ex-data t))))))

