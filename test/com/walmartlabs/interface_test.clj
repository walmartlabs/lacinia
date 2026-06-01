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
    [com.walmartlabs.test-utils :refer [expect-exception]]
    [com.walmartlabs.lacinia.schema :refer [compile]]))

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

(def compatible-field-multiplicity
  '{:interfaces {:named {:fields {:first_name {:type String}
                                  :last_name {:type (list String)}}}}
    :objects {:person {:implements [:named]
                       :fields {:first_name {:type String}
                                :last_name {:type (list (non-null String))}}}}})

(def incompatible-field-nullability
  '{:interfaces {:named {:fields {:first_name {:type (non-null String)}
                                  :last_name {:type String}}}}
    :objects {:person {:implements [:named]
                       :fields {:first_name {:type String}
                                :last_name {:type String}}}}})

(def compatible-field-nullability
  '{:interfaces {:named {:fields {:first_name {:type String}
                                  :last_name {:type String}}}}
    :objects {:person {:implements [:named]
                       :fields {:first_name {:type (non-null String)}
                                :last_name {:type String}}}}})

;; Basically checking error cases; success cases show up in other
;; tests.

(deftest object-must-implement-interface-fields
  (expect-exception
    "Missing interface field in object definition."
    {:object :person
     :field-name :first_name
     :interface-name :named}
    (compile field-not-implemented)))

(deftest field-must-be-compatible
  (testing "field type"
    (expect-exception "Object field is not compatible with extended interface type."
                      {:field-name :person/last_name
                       :interface-name :named}
                      (compile incompatible-field-type)))

  (testing "field multiplicity"
    (expect-exception
      "Object field is not compatible with extended interface type."
      {:field-name :person/last_name
       :interface-name :named}
      (compile incompatible-field-multiplicity))

    (is (some? (compile compatible-field-multiplicity))
        "Object fields are allowed to be a list of non-nulls, even if the interface field is a list of nullables."))

  (testing "field nullability"
    (expect-exception
      "Object field is not compatible with extended interface type."
      {:field-name :person/first_name
       :interface-name :named}
      (compile incompatible-field-nullability))

    (is (some? (compile compatible-field-nullability))
        "Object fields are allowed to be non-null, even if the interface field is nullable.")))

(def interface-implements-interface
  '{:interfaces {:node {:fields {:id {:type (non-null String)}}}
                 :resource {:implements [:node]
                            :fields {:id {:type (non-null String)}
                                     :url {:type String}}}}
    :objects {:article {:implements [:resource]
                        :fields {:id {:type (non-null String)}
                                 :url {:type String}
                                 :title {:type String}}}}})

(deftest interface-can-implement-interface
  (is (some? (compile interface-implements-interface))
      "schema with interface implementing interface should compile"))

(deftest object-transitively-implements-parent-interface
  (let [compiled (compile interface-implements-interface)]
    ;; :article implements :resource which implements :node.
    ;; :article should be a member of both :node and :resource.
    (is (contains? (get-in compiled [:node :members]) :article)
        "article should be a member of :node (transitively via :resource)")
    (is (contains? (get-in compiled [:resource :members]) :article)
        "article should be a member of :resource (directly)")))

(deftest interface-implements-interface-missing-field
  (let [invalid-schema (assoc-in interface-implements-interface
                                  [:interfaces :resource :fields]
                                  {:url {:type 'String}})]
    ;; :resource implements :node but doesn't declare :id
    (expect-exception
      "Missing interface field in interface definition."
      {:interface :resource
       :field-name :id
       :parent-interface-name :node}
      (compile invalid-schema))))

(deftest interface-implements-non-interface-fails
  ;; :resource tries to implement :article which is an object, not an interface
  (let [invalid-schema '{:interfaces {:node {:fields {:id {:type String}}}
                                      :resource {:implements [:article]
                                                 :fields {:id {:type String}}}}
                         :objects {:article {:implements [:node]
                                             :fields {:id {:type String}}}}}]
    (is (thrown-with-msg? Throwable
                          #"Interface `resource' implements type `article', which is not an interface."
                          (compile invalid-schema)))))

