(ns com.walmartlabs.lacinia.util-test
  "Utility functions tests."
  (:require [clojure.test :refer [deftest is]]
            [com.walmartlabs.lacinia.util :as util]))

(deftest attach-resolvers
  (let [schema {:person
                ^:object
                {:id {:type 'Int}
                 :name {:type 'String
                        :resolve :person-name}
                 :total-credit {:type 'Int
                                :resolve [:cents :totalCredit]}}
                :queries
                {:q1 {:type 'String
                      :resolve :q1}}}
        ;; Note: these are just for testing.  str, identity, and so forth
        ;; are handy constants, but are not field resolvers (which must return
        ;; a ResolverTuple).
        resolvers {:person-name str
                   :q1 identity
                   :cents (fn [name] name)}
        resolved-schema {:person
                         ^:object
                         {:id {:type 'Int}
                          :name {:type 'String
                                 :resolve str}
                          :total-credit {:type 'Int
                                         :resolve :totalCredit}}
                         :queries
                         {:q1 {:type 'String
                               :resolve identity}}}]
    (is (= resolved-schema (util/attach-resolvers schema resolvers)))))

(deftest attach-scalar-transformers
  (let [schema {:objects
                {:person {:fields {:id {:type 'Int}
                                   :name {:type 'String}}}}

                :scalars
                {:Foo
                 {:parse identity
                  :serialize :foo-serializer}

                 :Bar
                 {:parse :bar-parser
                  :serialize :bar-serializer}}}
        transformers {:foo-serializer str
                      :bar-parser int
                      :bar-serializer double}]
    (is (= {:objects {:person
                      {:fields {:id {:type 'Int}
                                :name {:type 'String}}}}

            :scalars
            {:Foo
             {:parse identity
              :serialize str}

             :Bar
             {:parse int
              :serialize double}}}
           (util/attach-scalar-transformers schema transformers)))))
