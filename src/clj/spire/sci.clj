(ns spire.sci
  (:require [sci.core :as sci]
            [sci.impl.vars :as vars]))

;; some helper macros for building sci bindings

(defmacro make-sci-bindings [namespace & [{:keys [exclusions only]
                                           :or {exclusions #{}
                                                only (constantly true)}}]]
  (->> (ns-publics namespace)
       (map second)
       (map (juxt identity meta))
       (map
        (fn [[var* {:keys [ns name macro]}]]
          (when (and
                 (only name)
                 (not (exclusions name)))
            [(list 'quote name)
             (if macro
               `(with-meta (deref (var ~(symbol var*)))
                  (-> (meta (var ~(symbol var*)))
                                 (select-keys [:doc :name :arglists])
                                 (assoc :sci.impl/built-in true
                                        :sci/macro true
                                        :ns (vars/->SciNamespace
                                             (quote ~ns)
                                             nil))))
               `(sci/new-var (quote ~name) ~(symbol var*)
                             (-> (meta (var ~(symbol var*)))
                                 (select-keys [:doc :name :arglists])
                                 (assoc :sci.impl/built-in true
                                        :ns (vars/->SciNamespace
                                             (quote ~ns)
                                             nil)))))])))
       (filter identity)
       (into {})))

#_ (macroexpand-1 '(make-sci-bindings spire.utils))
