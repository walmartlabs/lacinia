(ns com.walmartlabs.lacinia.executor.common
  "Common code needed by different approaches to query execution."
  (:require [clojure.set :as set]
            [clojure.spec :as s]
            [com.walmartlabs.lacinia.constants :refer [schema-key parsed-query-key]]
            [com.walmartlabs.lacinia.internal-utils
             :refer [ensure-seq cond-let to-message map-vals remove-vals q]]
            [com.walmartlabs.lacinia.schema :as schema]
            [com.walmartlabs.lacinia.resolve :as resolve]))


(defn ^:private ex-info-map
  ([field-selection]
   (ex-info-map field-selection {}))
  ([field-selection m]
   (merge m
          {:locations [(:location field-selection)]}
          (->> (select-keys field-selection [:query-path :arguments])
               (remove-vals nil?)))))

(defn ^:private enhance-errors
  "Using a (resolved) collection of (wrapped) error maps, add additional data to
  each error like location and arguments."
  [field-selection error-maps]
  (when (seq error-maps)
    (let [enhanced-data (ex-info-map field-selection nil)]
      (map
        #(merge % enhanced-data)
        error-maps))))

(defn ^:private field-selection-resolver
  "Returns the field resolver for the provided field selection.

  When the field-selection is on a concrete type, the resolve from the
  nested field-definition is returned.

  When the field selection is on an abstract type (an interface or union),
  then the concrete type is extracted from the value instead, and the corresponding
  field of the concrete type is used as the source for the field resolver."
  [schema field-selection value]
  (cond-let
    (:concrete-type? field-selection)
    (-> field-selection :field-definition :resolve)

    :let [{:keys [field]} field-selection
          type-name (schema/type-tag value)]

    (nil? type-name)
    (throw (ex-info "Sanity check: value type tag is nil on abstract type."
                    {:value value
                     :value-meta (meta value)}))

    :let [type (get schema type-name)]

    (nil? type)
    (throw (ex-info "Sanity check: invalid type tag on value."
                    {:type-name type-name
                     :value value
                     :value-meta (meta value)}))

    :else
    (or (get-in type [:fields field :resolve])
        (throw (ex-info "Sanity check: field not present."
                        {:type type-name
                         :value value
                         :value-meta (meta value)})))))


(defn resolve-value
  "Resolves the value for a field or fragment selection node, by passing the value to the
  appropriate resolver, and passing it through a chain of value enforcers.

  Returns a schema/ResolvedTuple of the resolved value for the node.
  The error maps in the tuple are enhanced with additional location and query-path data.

  For other types of selections, returns a tuple of the value."
  [selection context container-value]
  (if (= :field (:selection-type selection))
    (let [{:keys [arguments field-definition]} selection
          schema (get context schema-key)
          resolve-context (assoc context :com.walmartlabs.lacinia/selection selection)]
      (try
        (let [resolve (field-selection-resolver schema selection container-value)
             tuple (resolve resolve-context arguments container-value)]
          (resolve/resolve-as (resolve/resolved-value tuple)
                             (enhance-errors selection (resolve/resolve-errors tuple))))
        (catch clojure.lang.ExceptionInfo e
          (throw (ex-info (str "Error resolving field: " (to-message e))
                          (ex-info-map selection (ex-data e)))))))
    ;; Else, not a field selection:
    ;; Does this every happen?
    (resolve/resolve-as container-value)))

