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


(defn sci-ns-name [^sci.impl.vars.SciNamespace ns]
  (vars/getName ns))

(defn print-doc
  [m]
  (let [arglists (:arglists m)
        doc (:doc m)
        macro? (:sci/macro m)]
    (io/println "-------------------------")
    (io/println (str (when-let [ns* (:ns m)]
                       (str (sci-ns-name ns*) "/"))
                     (:name m)))
    (when arglists (io/println arglists))
    (when macro? (io/println "Macro"))
    (when doc (io/println " " doc))))

(defmacro doc
  [sym]
  `(if-let [var# (resolve '~sym)]
     (~'clojure.repl/print-doc (meta var#))
     #_(when (var? var#)
       (~'clojure.repl/print-doc (meta var#)))
     (if-let [ns# (find-ns '~sym)]
       (~'clojure.repl/print-doc (assoc (meta ns#)
                                        :name (ns-name ns#))))))


(def clojure-repl
  (assoc sci.impl.namespaces/clojure-repl
         'doc (with-meta @#'doc {:sci/macro true})
         'print-doc print-doc))
