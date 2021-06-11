(ns spire.pod.utils
  (:refer-clojure :exclude [read-string])
  (:require [spire.transport]
            [spire.ssh]
            [clojure.repl]))

(defn rename-second
  "given a clojure form, will rename the symbol in
  the second position. This is to rename def style
  forms. eg change (def foo 1) to (def bar 1)
  This would be done with (rename-second '(def foo 1) {'foo 'bar})
  "
  [form rename-hashmap]
  (if (rename-hashmap (second form))
    (concat (list (first form)
                  (rename-hashmap (second form)))
            (drop 2 form))
    form))

#_ (rename-second '(def foo [] bar) {'foo 'baz})

(defmacro process-source
  "given the fully qualified symbol for a macro or function, read that namespace's
  source code in and find that definition and extract the source code for it.
  rename-def contains a hashmap of symbols to be renamed and what to rename them to
  and this renames them in the second position. ie. (def RENAMETHIS ...)
  rename-ns renames any namespaced symbol found into a new namespace. keys and values
  of this hashmap need to be strings. eg {\"clojure.core\" \"my-new-core-ns\"}
  rename-symbol renames any symbol found into a new symbol
  "
  [sym & [{:keys [rename-ns
                  rename-def
                  rename-symbol
                  ]
           :or {rename-ns {}
                rename-def {}
                rename-symbol {}
                }}]]
  (binding [*print-meta* true]
    (->
     (clojure.repl/source-fn sym)
     (edamame.core/parse-string  {:all true
                                  :auto-resolve {:current (namespace sym)}})
     (rename-second rename-def)           ; rename the top level namespace
     (->> (clojure.walk/postwalk
           (fn [form]
             (if (symbol? form)
               (let [ns (namespace form)
                     sym-name (name form)
                     meta-data (dissoc (meta form) :row :col :end-row :end-col)]
                 (with-meta
                   (if (rename-symbol form)
                     (symbol (rename-symbol form))
                     (if (rename-ns ns)
                       (symbol (rename-ns ns) sym-name)
                       (symbol ns sym-name)))
                   meta-data))
               form))))
     prn-str)))

#_ (process-source spire.transport/ssh {:rename-symbol {open-connection bar/FOOOO}
                                        :rename-ns {"clojure.core" "foo"}})
#_ (process-source spire.ssh/*piped-stream-buffer-size*)
#_ (process-source spire.transport/debug)

(defmacro make-inlined-code-set-macros
  "given a namespace, extract all the definitions for the macros
  contained therein and return a babashka pod secription hashmap
  exclude can take a set of symbols to exclude from
  processing
  "
  [namespace & [{:keys [exclude rename-ns rename rename-symbol]
                 :or {exclude #{}
                      rename-ns {}
                      rename {}
                      rename-symbol {}}}]]
  (let [interns (ns-interns namespace)]
    (into []
          (filter identity
                  (for [sym (keys interns)]
                    (when (and (:macro (meta (interns sym))) ;; only process macros
                               (not (exclude sym)))
                      {"name" (str sym)
                       "code" `(process-source ~(symbol (interns sym))
                                               ~{:rename-ns (eval rename-ns)
                                                 :rename rename
                                                 :rename-symbol rename-symbol})
                       }))))))

#_ (make-inlined-code-set-macros spire.transport)
#_ (make-inlined-code-set-macros spire.transport {:exclude #{local ssh ssh-group}})
#_ (make-inlined-code-set-macros spire.transport {:exclude #{local ssh-group}
                                                  :rename-symbol {open-connection FOO/BAR}
                                                  :rename-ns {"clojure.core" "foo"}})

(defmacro make-function-lookup-table
  "given a namespace, create a hashmap table with keys of the
  function name, and values of the actual functions themselves"
  [ns pod-namespace-prefix]
  (->>
   (for [[k v] (ns-interns ns)]
     (let [{:keys [ns name macro private]} (meta v)]
       (when-not macro
         (if private
           ;; allow invoking private funcs if needed
           [(list 'quote (symbol (str pod-namespace-prefix (str ns)) (str k)))
            `(fn [& args#] (apply (var ~(symbol v)) args#))
            ]
           ;; public func
           [(list 'quote (symbol (str pod-namespace-prefix (str ns)) (str k)))
            (symbol v)
            ]))))
   (filter identity)
   (into {})))

#_ (make-lookup spire.transport)

(defmacro make-inlined-code-set
  "sometime non-macro code needs to be inlined. This macro creates the pod
  definitions for the code for a bunch of vars defined in a namespace
  pre-require can map pre require lines for each symbol defined.
  eg {:pre-require [[pod.lispyclouds.deferred-ns :as dns]
                    [clojure.string :as string]}
     }
  "
  [namespace syms & [{:keys [pre-declares
                             pre-requires
                             rename]
                      :or {pre-declares []
                           pre-requires []
                           rename {}}
                      :as opts}]]
  (let [interns (ns-interns namespace)]
    (into []
          (concat
           (if-not (empty? pre-declares)
             [{"name" "_pre-declares"
               "code" (->> pre-declares
                           (map #(format "(declare %s)" %))
                           (clojure.string/join " ")
                           )}]
             [])
           (if-not (empty? pre-requires)
               [{"name" "_pre-requires"
                 "code" (->> pre-requires
                             (mapv #(format "'%s" (pr-str %)))
                             (clojure.string/join " ")
                             (format "(require %s)"))}]
               [])
           (for [sym syms]
             {"name" (str (get rename sym sym))
              "code" `(process-source ~(symbol (interns sym)) ~opts)})))))

#_ (make-inlined-code-set spire.transport [ssh])
#_ (make-inlined-code-set
    spire.ssh [to-camel-case]
    {:pre-requires [[clojure.string :as string]
                    [clojure.set :as set]]}
    )

(defmacro make-inlined-namespace
  "join all the var definition bodies together for a namespace,
  and wrap in the babashka pod namespace header
  "
  [namespace & body]
  `{"name" ~(str namespace)
    "vars" (vec (apply concat (vector ~@body)))}
  )

#_ (make-inlined-namespace
    spire.transport
    (make-inlined-code-set spire.transport [ssh])
    (make-inlined-code-set spire.transport [debug])
                           )

(defmacro make-inlined-public-fns
  [namespace & [{:keys [exclude only include-private rename]
                 :or {exclude #{}
                      only (constantly true)
                      include-private false
                      rename {}}}]]
  (let [interns (if include-private
                  (ns-interns namespace)
                  (ns-publics namespace))]
    (into []
          (filter identity
                  (for [sym (keys interns)]
                    (when (and (not (exclude sym))
                               (not (:macro (meta (interns sym))))
                               (only sym))
                      {"name" (str (get rename sym sym))}))))))

#_ (make-inlined-public-fns spire.transport)

(defmacro make-lookup [ns pod-namespace-prefix]
  (->>
   (for [[k v] (ns-interns ns)]
     (let [{:keys [ns name macro private]} (meta v)]
       (when-not macro
         (if private
           ;; allow invoking private funcs if needed
           [(list 'quote (symbol (str pod-namespace-prefix (str ns)) (str k)))
            `(fn [& args#] (apply (var ~(symbol v)) args#))
            ]
           ;; public func
           [(list 'quote (symbol (str pod-namespace-prefix (str ns)) (str k)))
            (symbol v)
            ]))))
   (filter identity)
   (into {})))

#_ (make-lookup spire.ssh "pod.epiccastle.")
