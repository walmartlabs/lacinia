(ns com.walmartlabs.lacinia.vendor.ordered.map
  (:use [com.walmartlabs.lacinia.vendor.ordered.common :only [change! Compactable compact]]
        [com.walmartlabs.lacinia.vendor.ordered.set :only [ordered-set]]
        [com.walmartlabs.lacinia.vendor.useful.experimental.delegate :only [delegating-deftype]])
  (:require [clojure.string :as s])
  (:import (clojure.lang APersistentMap
                         IPersistentMap
                         IPersistentCollection
                         IPersistentVector
                         IEditableCollection
                         ITransientMap
                         ITransientVector
                         IHashEq
                         IObj
                         IFn
                         Seqable
                         MapEquivalence
                         Reversible
                         MapEntry
                         ;; Indexed, maybe add later?
                         ;; Sorted almost certainly not accurate
                         )
           (java.util Map Map$Entry)))

;; We could use compile-if technique here, but hoping to avoid
;; an AOT issue using this way instead.
(def hasheq-ordered-map
  (or (resolve 'clojure.core/hash-unordered-coll)
      (fn old-hasheq-ordered-map [^Seqable m]
        (reduce (fn [acc ^MapEntry e]
                  (let [k (.key e), v (.val e)]
                    (unchecked-add ^Integer acc ^Integer (bit-xor (hash k) (hash v)))))
                0 (.seq m)))))

(defn entry [k v i]
  (MapEntry. k (MapEntry. i v)))

(declare transient-ordered-map)

(delegating-deftype OrderedMap [^IPersistentMap backing-map
                                ^IPersistentVector order]
  {backing-map {IPersistentMap [(count [])]
                Map [(size [])
                     (containsKey [k])
                     (isEmpty [])
                     (keySet [])]}}
  ;; tagging interfaces
  MapEquivalence

  IPersistentMap
  (equiv [this other]
    (and (instance? Map other)
         (= (.count this) (.size ^Map other))
         (every? (fn [^MapEntry e]
                   (let [k (.key e)]
                     (and (.containsKey ^Map other k)
                          (= (.val e) (.get ^Map other k)))))
                 (.seq this))))
  (entryAt [this k]
    (let [v (get this k ::not-found)]
      (when (not= v ::not-found)
        (MapEntry. k v))))
  (valAt [this k]
    (.valAt this k nil))
  (valAt [this k not-found]
    (if-let [^MapEntry e (.get ^Map backing-map k)]
      (.val e)
      not-found))

  IFn
  (invoke [this k]
    (.valAt this k))
  (invoke [this k not-found]
    (.valAt this k not-found))

  Map
  (get [this k]
    (.valAt this k))
  (containsValue [this v]
    (boolean (seq (filter #(= % v) (.values this)))))
  (values [this]
    (map (comp val val) (.seq this)))

  Object
  (toString [this]
    (str "{" (s/join ", " (for [[k v] this] (str k " " v))) "}"))
  (equals [this other]
    (.equiv this other))
  (hashCode [this]
    (APersistentMap/mapHash this))
  IHashEq
  (hasheq [this]
    (hasheq-ordered-map this))

  IPersistentMap
  (empty [this]
    (OrderedMap. (-> {} (with-meta (meta backing-map))) []))
  (cons [this obj]
    (condp instance? obj
      Map$Entry (let [^Map$Entry e obj]
                  (.assoc this (.getKey e) (.getValue e)))
      IPersistentVector (if (= 2 (count obj))
                          (.assoc this (nth obj 0) (nth obj 1))
                          (throw (IllegalArgumentException.
                                  "Vector arg to map conj must be a pair")))
      (persistent! (reduce (fn [^ITransientMap m ^Map$Entry e]
                             (.assoc m (.getKey e) (.getValue e)))
                           (transient this)
                           obj))))

  (assoc [this k v]
    (if-let [^MapEntry e (.get ^Map backing-map k)]
      (let [old-v (.val e)]
        (if (identical? old-v v)
          this
          (let [i (.key e)]
            (OrderedMap. (.cons backing-map (entry k v i))
                         (.assoc order i (MapEntry. k v))))))
      (OrderedMap. (.cons backing-map (entry k v (.count order)))
                   (.cons order (MapEntry. k v)))))
  (without [this k]
    (if-let [^MapEntry e (.get ^Map backing-map k)]
      (OrderedMap. (.without backing-map k)
                   (.assoc order (.key e) nil))
      this))
  (seq [this]
    (seq (keep identity order)))
  (iterator [this]
    (clojure.lang.SeqIterator. (.seq this)))
  (entrySet [this]
    ;; not performant, but i'm not going to implement another whole java interface from scratch just
    ;; because rich won't let us inherit from AbstractSet
    (apply ordered-set this))

  IObj
  (meta [this]
    (.meta ^IObj backing-map))
  (withMeta [this m]
    (OrderedMap. (.withMeta ^IObj backing-map m) order))

  IEditableCollection
  (asTransient [this]
    (transient-ordered-map this))

  Reversible
  (rseq [this]
    (seq (keep identity (rseq order))))

  Compactable
  (compact [this]
    (into (empty this) this)))

(def ^{:private true,
       :tag OrderedMap} empty-ordered-map (empty (OrderedMap. nil nil)))

(defn ordered-map
  "Return a map with the given keys and values, whose entries are
sorted in the order that keys are added. assoc'ing a key that is
already in an ordered map leaves its order unchanged. dissoc'ing a
key and then later assoc'ing it puts it at the end, as if it were
assoc'ed for the first time. Supports transient."
  ([] empty-ordered-map)
  ([coll]
     (into empty-ordered-map coll))
  ([k v & more]
     (apply assoc empty-ordered-map k v more)))

;; contains? is broken for transients. we could define a closure around a gensym
;; to use as the not-found argument to a get, but deftype can't be a closure.
;; instead, we pass `this` as the not-found argument and hope nobody makes a
;; transient contain itself.

(delegating-deftype TransientOrderedMap [^{:unsynchronized-mutable true, :tag ITransientMap} backing-map,
                                         ^{:unsynchronized-mutable true, :tag ITransientVector} order]
  {backing-map {ITransientMap [(count [])]}}
  ITransientMap
  (valAt [this k]
    (.valAt this k nil))
  (valAt [this k not-found]
    (if-let [^MapEntry e (.valAt backing-map k)]
      (.val e)
      not-found))
  (assoc [this k v]
    (let [^MapEntry e (.valAt backing-map k this)
          vector-entry (MapEntry. k v)
          i (if (identical? e this)
              (do (change! order .conj vector-entry)
                  (dec (.count order)))
              (let [idx (.key e)]
                (change! order .assoc idx vector-entry)
                idx))]
      (change! backing-map .conj (entry k v i))
      this))
  (conj [this e]
    (let [[k v] e]
      (.assoc this k v)))
  (without [this k]
    (let [^MapEntry e (.valAt backing-map k this)]
      (when-not (identical? e this)
        (let [i (.key e)]
          (change! backing-map dissoc! k)
          (change! order assoc! i nil)))
      this))
  (persistent [this]
    (OrderedMap. (.persistent backing-map)
                 (.persistent order))))

(defn transient-ordered-map [^OrderedMap om]
  (TransientOrderedMap. (.asTransient ^IEditableCollection (.backing-map om))
                        (.asTransient ^IEditableCollection (.order om))))

(defmethod print-method OrderedMap [o ^java.io.Writer w]
  (.write w "#ordered/map ")
  (print-method (seq o) w))
