(ns spire.sci)

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
               `(with-meta (deref (var ~(symbol var*))) {:sci/macro true})
               (symbol var*))])))
       (filter identity)
       (into {})))

#_ (macroexpand-1 '(make-sci-bindings spire.utils))
