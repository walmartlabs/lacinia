; Copyright (c) 2020-present Walmart, Inc.
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

(ns com.walmartlabs.lacinia.tracing
  "Support for tracing of requests, as per the
  [Apollo tracing specification](https://github.com/apollographql/apollo-tracing)."
  {:added "0.38.0"}
  (:import
    (java.time.format DateTimeFormatter)
    (java.time ZonedDateTime ZoneOffset)))

(defn timestamp
  "Returns the current time as a RFC-3339 string."
  []
  (.format DateTimeFormatter/ISO_INSTANT (ZonedDateTime/now ZoneOffset/UTC)))

(defn duration
  "Returns the number of nanoseconds since the start offset."
  [start-offset]
  (- (System/nanoTime) start-offset))

(defn create-timing-start
  "Captures the initial timing information that is used later to calculate
  offset from the start time (via [[offset-from-start]])."
  []
  {:start-time (timestamp)
   :start-nanos (System/nanoTime)})

(defn offset-from-start
  "Given the initial timing information from [[create-timing-start]], calculates
  the start offset for an operation, as a number of nanoseconds."
  [timing-start]
  (-> timing-start :start-nanos duration))

(defn ^:private xf-phase
  [phase]
  (let [{:keys [start-offset duration]} phase]
    {:startOffset start-offset
     :duration duration}))

(defn inject-tracing
  "Injects the tracing data into result map, under :extensions."
  [result timing-start parse-phase validation-phase resolver-timings]
  (let [{:keys [start-time start-nanos]} timing-start]
    (assoc-in result [:extensions :tracing]
              {:version 1
               :startTime start-time
               :endTime (timestamp)
               :duration (duration start-nanos)
               :parsing (xf-phase parse-phase)
               :validation (xf-phase validation-phase)
               :execution {:resolvers resolver-timings}})))

(defn enable-tracing
  "Modifies the application context to enable tracing.

  Tracing can signficantly slow down execution of the query document, both because
  of the essential overhead, and because certain optimizations are disabled during a traced
  request."
  [context]
  (assoc context ::enabled? true))
