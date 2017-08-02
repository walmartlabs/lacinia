(ns com.walmartlabs.lacinia.interfaces-test
  (:require [clojure.test :as test :refer [deftest is testing]]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.test-utils :refer [is-thrown]]))

(def starship-data
  (->> [{:id "001"
         :name "Millennium Falcon"
         :length 34.37
         :class "Light freighter"
         ::type :starship}
        {:id "002"
         :name "X-wing"
         :length 12.5
         :class "Starfighter"
         ::type :starship}
        {:id "003"
         :name "Executor"
         :length 19000
         :class "Star dreadnought"
         ::type :starship}
        {:id "004"
         :name "Death Star"
         :length 120000
         :class "Deep Space Mobile Battlestation"
         ::type :starship}]
       (map (juxt :id identity))
       (into {})))

(defn ^:private get-starship
  [id]
  (get starship-data id))

(def ^:private test-schema
  {:enums
   {:unit {:values [:METER :FOOT]}}

   :interfaces
   {:vehicle {:fields {:id {:type '(non-null String)}
                       :name {:type '(non-null String)}
                       :length {:type 'Float
                                :args {:unit {:type :unit}}}
                       :class {:type 'String}}}}
   :objects
   {:starship
    {:implements [:vehicle]
     :fields {:id {:type '(non-null String)}
              :name {:type '(non-null String)}
              :length {:type 'Float
                       :args {:unit {:type :unit
                                     :default-value :METER}}
                       :resolve (fn [ctx args v]
                                  (let [{:keys [unit]} args
                                        length (:length v)]
                                    (if-not (= unit :METER)
                                      (when length
                                        (* length 3.28))
                                      length)))}
              :class {:type 'String}}}}

   :queries
   {:starship
    {:type :starship
     :args {:id {:type '(non-null String)}}
     :resolve (fn [ctx args v]
                (let [{:keys [id]} args]
                  (get-starship id)))}}})

(deftest compatible-arguments
  (testing "field argument is optional and had default value"
    (let [compiled-schema (schema/compile test-schema {:default-field-resolver schema/hyphenating-default-field-resolver})
          q1 "query FetchStarship {
                 starship(id: \"001\") {
                    name
                    class
                    length(unit: FOOT)
                 }
              }"
          q2 "query FetchStarship {
                starship(id: \"001\") {
                   name
                   class
                   length
                }
             }"]
      (is (= {:data {:starship {:name "Millennium Falcon"
                                :class "Light freighter"
                                :length 112.73359999999998}}}
             (execute compiled-schema q1 nil nil))
          "schema should compile and query is successful")
      (is (= {:data {:starship {:name "Millennium Falcon"
                                :class "Light freighter"
                                :length 34.37}}}
             (execute compiled-schema q2 nil nil))
          "schema should compile and query is successful")))

  (testing "field argument is required by the interface and not present in the object"
    (let [invalid-schema (-> test-schema
                             (assoc-in [:interfaces :vehicle :fields :length :args :unit :type]
                                       '(non-null :unit))
                             (assoc-in [:objects :speeder]
                                       {:implements [:vehicle]
                                        :fields {:id {:type '(non-null String)}
                                                 :name {:type '(non-null String)}
                                                 ;; :length field is missing argument :unit
                                                 :length {:type 'Float}
                                                 :class {:type 'String}}}))]
      (is-thrown [e (schema/compile invalid-schema {:default-field-resolver schema/hyphenating-default-field-resolver})]
                 (is (= (.getMessage e) "Missing interface field argument in object definition.")))))

  (testing "field argument is not required by the interface and is not present in the object"
    (let [invalid-schema (-> test-schema
                             (assoc-in [:interfaces :vehicle :fields :length :args :unit :type] :unit)
                             (assoc-in [:objects :speeder]
                                       {:implements [:vehicle]
                                        :fields {:id {:type '(non-null String)}
                                                 :name {:type '(non-null String)}
                                                 ;; :length field is missing argument :unit
                                                 :length {:type 'Float}
                                                 :class {:type 'String}}}))]
      (is-thrown [e (schema/compile invalid-schema {:default-field-resolver schema/hyphenating-default-field-resolver})]
                 (is (= (.getMessage e) "Missing interface field argument in object definition.")))))

  (testing "field argument in the interface has a different type to field argument in the object"
    (let [invalid-schema (-> test-schema
                             (assoc-in [:objects :speeder]
                                       {:implements [:vehicle]
                                        :fields {:id {:type '(non-null String)}
                                                 :name {:type '(non-null String)}
                                                 :length {:type 'Float
                                                          ;; invalid type of :unit arg
                                                          :args {:unit {:type 'String}}}
                                                 :class {:type 'String}}}))]
      (is-thrown [e (schema/compile invalid-schema {:default-field-resolver schema/hyphenating-default-field-resolver})]
                 (is (= (.getMessage e) "Object field's argument is not compatible with extended interface's argument type."))))

    (let [invalid-schema (-> test-schema
                             (assoc-in [:interfaces :vehicle :fields :length :args :unit :type]
                                       '(non-null :unit))
                             (assoc-in [:objects :speeder]
                                       {:implements [:vehicle]
                                        :fields {:id {:type '(non-null String)}
                                                 :name {:type '(non-null String)}
                                                 :length {:type 'Float
                                                          ;; invalid type of :unit arg (it's nullable)
                                                          :args {:unit {:type :unit}}}
                                                 :class {:type 'String}}}))]
      (is-thrown [e (schema/compile invalid-schema {:default-field-resolver schema/hyphenating-default-field-resolver})]
                 (is (= (.getMessage e) "Object field's argument is not compatible with extended interface's argument type.")))))

  (testing "object includes additional field arguments that are not defined in the interface field"
    (let [schema (-> test-schema
                     (update-in [:objects :starship :fields :length :args]
                                #(assoc % :precision {:type 'Int})))]
      (is (map? (schema/compile schema {:default-field-resolver schema/hyphenating-default-field-resolver}))
          "should compile schema without any errors"))))
