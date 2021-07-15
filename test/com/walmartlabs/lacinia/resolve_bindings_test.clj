; Copyright (c) 2021-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.resolve-bindings-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.core.async :refer [chan go close! <!!]]
            [com.walmartlabs.lacinia.resolve :as resolve])
  (:import (java.util.concurrent ThreadPoolExecutor TimeUnit ArrayBlockingQueue ExecutorService)))

(def ^:private ^:dynamic *bound* :default)

(def ^:private *states (atom []))

(defn ^:private reset-*states
  [f]
  (try
    (f)
    (finally
      (reset! *states []))))

(defn ^:private add-state
  "Capture the state of *bound*."
  [tag]
  (swap! *states conj [tag *bound*]))

(use-fixtures :each
              reset-*states)

(defn ^:private ^ExecutorService new-executor
  []
  (ThreadPoolExecutor. 1 5 1 TimeUnit/SECONDS (ArrayBlockingQueue. 1)))

;; First, normal case where there is no executor for the callback

(deftest bindings-conveyed-normally
  (let [resolved (chan)
        promise (resolve/resolve-promise)]
    (add-state "before")
    (binding [*bound* :override]
      (add-state "during")
      (resolve/on-deliver! promise
                           (fn [_]
                             (add-state "on-deliver!")
                             (close! resolved)))
      ;; We use the go macro as it is known to properly convey bindings
      (go
        (add-state "in go block")
        (resolve/deliver! promise true)))
    (<!! resolved)
    (add-state "after")
    (is (= [["before" :default]
            ["during" :override]
            ["in go block" :override]
            ["on-deliver!" :override]
            ["after" :default]]
           @*states))))

(deftest bindings-conveyed-through-executor
  (let [resolved (chan)
        executor (new-executor)]
    (try
      (add-state "before")
      (binding [*bound* :override
                resolve/*callback-executor* executor]
        (add-state "during")
        ;; bindings are captured at the time the promise is created
        (let [promise (resolve/resolve-promise)]
          (resolve/on-deliver! promise
                               (fn [_]
                                 (add-state "on-deliver!")
                                 (close! resolved)))
          (go
            (add-state "in go block")
            (resolve/deliver! promise true)))
        (<!! resolved))
      (add-state "after")
      (is (= [["before" :default]
              ["during" :override]
              ["in go block" :override]
              ;; The following is where it would go wrong without the fix:
              ["on-deliver!" :override]
              ["after" :default]]
             @*states))
      (finally
        (.shutdown executor)))))
