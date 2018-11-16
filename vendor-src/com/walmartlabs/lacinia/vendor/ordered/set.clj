(ns com.walmartlabs.lacinia.vendor.ordered.set
  (:use [com.walmartlabs.lacinia.vendor.ordered.common :only [Compactable compact change!]])
  (:require [clojure.string :as s])
  (:import (clojure.lang IPersistentSet ITransientSet IEditableCollection
                         IPersistentMap ITransientMap ITransientAssociative
                         IPersistentVector ITransientVector IHashEq
                         Associative Seqable SeqIterator Reversible IFn IObj)
           (java.util Set Collection)))

(declare transient-ordered-set)

;; We could use compile-if technique here, but hoping to avoid
;; an AOT issue using this way instead.
(def hasheq-ordered-set
  (or (resolve 'clojure.core/hash-unordered-coll)
      (fn old-hasheq-ordered-set [^Seqable s]
        (reduce + (map hash (.seq s))))))

(deftype OrderedSet [^IPersistentMap k->i
                     ^IPersistentVector i->k]
  IPersistentSet
  (disjoin [this k]
    (if-let [i (.valAt k->i k)]
      (OrderedSet. (dissoc k->i k)
                   (assoc i->k i ::empty))
      this))
  (cons [this k]
    (if-let [i (.valAt k->i k)]
      this
      (OrderedSet. (.assoc ^Associative k->i k (.count i->k))
                   (.cons i->k k))))
  (seq [this]
    (seq (remove #(identical? ::empty %) i->k)))
  (empty [this]
    (OrderedSet. (-> {} (with-meta (meta k->i)))
                 []))
  (equiv [this other]
    (.equals this other))
  (get [this k]
    (when (.valAt k->i k) k))
  (count [this]
    (.count k->i))

  IObj
  (meta [this]
    (.meta ^IObj k->i))
  (withMeta [this m]
    (OrderedSet. (.withMeta ^IObj k->i m)
                 i->k))

  Compactable
  (compact [this]
    (into (empty this) this))

  Object
  (toString [this]
    (str "#{" (clojure.string/join " " (map str this)) "}"))
  (hashCode [this]
    (reduce + (map #(.hashCode ^Object %) (.seq this))))
  (equals [this other]
    (or (identical? this other)
        (and (instance? Set other)
             (let [^Set s other]
               (and (= (.size this) (.size s))
                    (every? #(.contains s %) (.seq this)))))))

  IHashEq
  (hasheq [this]
    (hasheq-ordered-set this))
  
  Set
  (iterator [this]
    (SeqIterator. (.seq this)))
  (contains [this k]
    (.containsKey k->i k))
  (containsAll [this ks]
    (every? #(.contains this %) ks))
  (size [this]
    (.count this))
  (isEmpty [this]
    (zero? (.count this)))
  (^objects toArray [this ^objects dest]
    (reduce (fn [idx item]
              (aset dest idx item)
              (inc idx))
            0, (.seq this))
    dest)
  (toArray [this]
    (.toArray this (object-array (.count this))))

  Reversible
  (rseq [this]
    (seq (remove #(identical? ::empty %) (rseq i->k))))

  IEditableCollection
  (asTransient [this]
    (transient-ordered-set this))
  IFn
  (invoke [this k] (when (.contains this k) k)))

(def ^{:private true,
       :tag OrderedSet} empty-ordered-set (empty (OrderedSet. nil nil)))

(defn ordered-set
  "Return a set with the given items, whose items are sorted in the
order that they are added. conj'ing an item that was already in the
set leaves its order unchanged. disj'ing an item and then later
conj'ing it puts it at the end, as if it were being added for the 
first time. Supports transient.

Note that clojure.set functions like union, intersection, and
difference can change the order of their input sets for efficiency
purposes, so may not return the order you expect given ordered sets
as input."
  ([] empty-ordered-set)
  ([& xs] (into empty-ordered-set xs)))

(deftype TransientOrderedSet [^{:unsynchronized-mutable true
                                :tag ITransientMap} k->i,
                              ^{:unsynchronized-mutable true
                                :tag ITransientVector} i->k]
  ITransientSet
  (count [this]
    (.count k->i))
  (get [this k]
    (when (.valAt k->i k) k))
  (disjoin [this k]
    (let [i (.valAt k->i k)]
      (when i
        (change! k->i .without k)
        (change! i->k .assocN i ::empty)))
    this)
  (conj [this k]
    (let [i (.valAt k->i k)]
      (when-not i
        (change! ^ITransientAssociative k->i .assoc k (.count i->k))
        (change! i->k conj! k)))
    this)
  (contains [this k]
    (boolean (.valAt k->i k)))
  (persistent [this]
    (OrderedSet. (.persistent k->i)
                 (.persistent i->k))))

(defn transient-ordered-set [^OrderedSet os]
  (TransientOrderedSet. (transient (.k->i os))
                        (transient (.i->k os))))

(defn into-ordered-set
  [items]
  (into empty-ordered-set items))

(defmethod print-method OrderedSet [o ^java.io.Writer w]
  (.write w "#ordered/set ")
  (print-method (seq o) w))
