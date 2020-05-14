(ns spire.sci
  (:require [sci.core :as sci]
            [sci.impl.vars :as vars]))

;; some helper macros for building sci bindings

(defmacro make-sci-bindings [namespace & [{:keys [exclusions only]
                                           :or {exclusions #{}
                                                only (constantly true)}}]]
  (let [ns-sym (gensym "ns-")
        mapping
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
                                   :ns ~ns-sym)))
                     `(sci/new-var (quote ~name) ~(symbol var*)
                                   (-> (meta (var ~(symbol var*)))
                                       (select-keys [:doc :name :arglists])
                                       (assoc :sci.impl/built-in true
                                              :ns ~ns-sym))))])))
             (filter identity)
             (into {}))]
    `(let [~ns-sym (vars/->SciNamespace (quote ~namespace) nil)]
       ~mapping)))

#_ (macroexpand-1 '(make-sci-bindings spire.utils))


(defmacro make-sci-bindings-clean [namespace & [{:keys [exclusions only]
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
               `(with-meta (deref (var ~(symbol var*))) {:sci/macro true})
               (symbol var*))])))
       (filter identity)
       (into {})))
