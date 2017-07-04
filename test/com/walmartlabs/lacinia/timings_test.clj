(ns com.walmartlabs.lacinia.timings-test
  "Tests for the optional timing logic."
  (:require
    [clojure.test :refer [deftest is]]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.util :as util]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.test-utils :refer [simplify]]
    [com.walmartlabs.lacinia :refer [execute]]
    [com.walmartlabs.lacinia.schema :as schema]))

(def ^:private enable-timing {:com.walmartlabs.lacinia/enable-timing? true})

(defn ^:private resolve-fast
  [_ args _]
  {:simple (:value args)
   ::slow {:simple (:nested_value args)}
   ::delay (:delay args)})

(defn ^:private resolve-slow
  [_ _ value]
  (let [resolved-value (resolve/resolve-promise)
        f (fn []
            (Thread/sleep (::delay value))
            (resolve/deliver! resolved-value (::slow value)))
        thread (Thread. ^Runnable f)]
    (.start thread)
    resolved-value))

(def ^:private compiled-schema
  (-> (io/resource "timing-schema.edn")
      slurp
      edn/read-string
      (util/attach-resolvers {:resolve-fast resolve-fast
                              :resolve-slow resolve-slow})
      schema/compile))

(defn ^:private q
  ([query]
   (q query nil))
  ([query context]
   (-> (execute compiled-schema query nil context)
       simplify)))

(deftest timings-are-off-by-default
  (is (= {:data {:root {:simple "fast!"
                        :slow {:simple "slow!!"}}}}
         (q "{ root(delay: 50) { simple slow { simple }}}"))))

(deftest timing-is-collected-when-enabled
  (let [result (q "{ root(delay: 50) { simple slow { simple }}}" enable-timing)]
    (is (-> result :extensions :timing empty? not)
        "Some timings were collected.")))

(deftest does-not-collect-timing-for-default-resolvers
  (let [result (q "{ root(delay: 50) { simple slow { simple }}}" enable-timing)]
    (is (= nil (-> result :extensions :timing :root :simple))
        "Some timings were collected.")))

(deftest collects-timing-for-provided-resolvers
  (doseq [delay [25 50 75]
          :let [result (q (str "{ root(delay: " delay ") { slow { simple }}}") enable-timing)
                timings (get-in result [:extensions :timing :root :slow :execution/timings])
                elapsed-time (-> timings first :elapsed)]]
    ;; Allow for a bit of overhead; Thread/sleep is quite inexact.
    (is (<= delay elapsed-time (* delay 10)))
    ;; Check that :start and :finish are both present and add up
    (is (= elapsed-time
           (- (-> timings first :finish)
              (-> timings first :start))))))

(deftest collects-timing-for-each-execution
  (let [result (q "{ hare: root(delay: 5) { slow { simple }}
                     tortoise: root(delay: 50) { slow { simple }}
                   }"
                  enable-timing)
        elapsed-times (->> (get-in result [:extensions :timing :root :slow :execution/timings])
                           (mapv :elapsed))]
    (is (= 2 (count elapsed-times)))
    (is (<= 5 (elapsed-times 0)))
    (is (<= 50 (elapsed-times 1)))))

