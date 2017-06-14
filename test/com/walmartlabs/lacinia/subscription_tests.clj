(ns com.walmartlabs.lacinia.subscription-tests
  (:require
    [com.walmartlabs.test-utils :refer [execute]]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [com.walmartlabs.lacinia.util :as util]))

;; There's not a whole lot we can do here, as most of the support has to come from the web tier code, e.g.,
;; pedestal-lacinia.

#_ (def ^:private compiled-schema
  (-> (io/resource "subscriptions-schema.edn")
      slurp
      edn/read-string
      (util/attach-resolvers {})
      #_ (util/attach-streamers {})))
