(ns tracing-demo
  (:require
    [com.walmartlabs.lacinia :as lacinia]
    [com.walmartlabs.lacinia.tracing :as tracing]
    [com.walmartlabs.lacinia-test :as lacinia-test]
    [com.walmartlabs.lacinia.resolve :as resolve]
    [com.walmartlabs.test-utils :refer [simplify]])
  (:import (java.util.concurrent ThreadPoolExecutor TimeUnit SynchronousQueue)))

(def star-wars-schema lacinia-test/default-schema)

(comment
  (let [queue (SynchronousQueue.)
        thread-pool (ThreadPoolExecutor. 5 10 1 TimeUnit/SECONDS queue)]
    ;; Ensure all core thread are started and ready, however, for the tiny
    ;; resolvers in the example, it's hard to show parallel behavior. Need some I/O.
    (while (.prestartCoreThread thread-pool))
    (try
      (binding [resolve/*callback-executor* thread-pool]

        (simplify
          (lacinia/execute
            star-wars-schema "
  {
    luke: human(id: \"1000\") { friends { name }}
    leia: human(id: \"1003\") { name }
  }"
            nil
            (tracing/enable-tracing nil))))
      (finally
        (.shutdown thread-pool))))


  )