; Copyright (c) 2018-present Walmart, Inc.
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
(ns com.walmartlabs.lacinia.vendor.useful.experimental.delegate
  (:use com.walmartlabs.lacinia.vendor.useful.debug)
  (:require [com.walmartlabs.lacinia.vendor.useful.ns :as ns]))

(defn canonical-name
  "Resolve a symbol in the current namespace; but intead of returning its value,
   return a canonical name that can be used to name the same thing in any
   namespace."
  [sym]
  (if-let [val (resolve sym)]
    (condp instance? val
      java.lang.Class (symbol (pr-str val))
      clojure.lang.Var (ns/var-name val)
      (throw (IllegalArgumentException.
              (format "%s names %s, an instance of %s, which has no canonical name."
                      sym val (class val)))))
    sym))

(defn parse-deftype-specs
  "Given a mess of deftype specs, possibly with classes/interfaces specified multiple times,
  collapse it into a map like {interface => (method1 method2...)}.
  Needed because core.deftype only allows specifying a class ONCE, so our delegating versions would
  clash with client's custom methods."
  [decls]
  (loop [ret {}, curr-key nil, decls decls]
    (if-let [[x & xs] (seq decls)]
      (if (seq? x)
        (let [mname (symbol (name (first x)))
              nargs (count (second x))]
          (recur (assoc-in ret [curr-key [mname nargs]] x),
                 curr-key, xs))
        (let [interface-name (canonical-name x)]
          (recur (update-in ret [interface-name] #(or % {})),
                 interface-name, xs)))
      ret)))

(defn emit-deftype-specs
  "Given a map returned by aggregate, spit out a flattened deftype body."
  [specs]
  (apply concat
         (for [[interface methods] specs]
           (cons interface
                 (for [[[method-name num-args] method] methods]
                   method)))))

(letfn [;; Output the method body for a delegating implementation
        (delegating-method [method-name args delegate]
          `(~method-name [~'_ ~@args]
             (. ~delegate (~method-name ~@args))))

        ;; Create a series of Interface (method...) (method...) expressions,
        ;; suitable for creating the entire body of a deftype or reify.
        (type-body [delegate-map other-args]
          (let [our-stuff (for [[send-to interfaces] delegate-map
                                [interface which] interfaces
                                :let [send-to (vary-meta send-to
                                                         assoc :tag interface)]
                                [name args] which]
                            [interface (delegating-method name args send-to)])]
            (emit-deftype-specs
             (parse-deftype-specs
              (apply concat other-args our-stuff)))))]

  (defmacro delegating-deftype
    "Shorthand for defining a new type with deftype, which delegates the methods you name to some
    other object or objects. Delegates are usually a member field, but can be any expression: the
    expression will be evaluated every time a method is delegated. The delegate object (or
    expression) will be type-hinted with the type of the interface being delegated.

    The delegate-map argument should be structured like:
      {object-to-delegate-to {Interface1 [(method1 [])
                                          (method2 [foo bar baz])]
                              Interface2 [(otherMethod [other])]},
       another-object {Interface1 [(method3 [whatever])]}}.

    This will cause your deftype to include an implementation of Interface1.method1 which does its
    work by forwarding to (.method1 object-to-delegate-to), and likewise for the other
    methods. Arguments will be forwarded on untouched, and you should not include a `this`
    parameter. Note especially that you can have methods from Interface1 implemented by delegating
    to multiple objects if you choose, and can also include custom implementations for the remaining
    methods of Interface1 if you have no suitable delegate.

    Arguments after `delegate-map` are as with deftype, although if deftype ever has options defined
    for it, delegating-deftype may break with them."
    [cname [& fields] delegate-map & deftype-args]
    `(deftype ~cname [~@fields]
       ~@(type-body delegate-map deftype-args)))

  (defmacro delegating-defrecord
    "Like delegating-deftype, but creates a defrecod body instead of a deftype."
    [cname [& fields] delegate-map & deftype-args]
    `(defrecord ~cname [~@fields]
       ~@(type-body delegate-map deftype-args)))

  (defmacro delegating-reify
    "Like delegating-deftype, but creates a reify body instead of a deftype."
    [delegate-map & reify-args]
    `(reify ~@(type-body delegate-map reify-args))))
