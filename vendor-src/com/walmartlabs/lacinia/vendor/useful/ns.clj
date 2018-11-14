(ns com.walmartlabs.lacinia.vendor.useful.ns)

(defn var-name
  "Get the namespace-qualified name of a var."
  [v]
  (apply symbol (map str ((juxt (comp ns-name :ns)
                                :name)
                          (meta v)))))

(defn alias-var
  "Create a var with the supplied name in the current namespace, having the same
  metadata and root-binding as the supplied var."
  [name ^clojure.lang.Var var]
  (apply intern *ns* (with-meta name (merge {:dont-test (str "Alias of " (var-name var))}
                                            (meta var)
                                            (meta name)))
         (when (.hasRoot var) [@var])))

(defmacro defalias
  "Defines an alias for a var: a new var with the same root binding (if
  any) and similar metadata. The metadata of the alias is its initial
  metadata (as provided by def) merged into the metadata of the original."
  [dst src]
  `(alias-var (quote ~dst) (var ~src)))

(defn alias-ns
  "Create vars in the current namespace to alias each of the public vars in
  the supplied namespace."
  [ns-name]
  (require ns-name)
  (doseq [[name var] (ns-publics (the-ns ns-name))]
    (alias-var name var)))
