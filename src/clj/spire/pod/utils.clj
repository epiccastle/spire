(ns spire.pod.utils
  (:refer-clojure :exclude [read-string])
  (:require [bencode.core :refer [read-bencode write-bencode]]
            [spire.transport]
            [spire.ssh]
            [clojure.edn :as edn]
            [clojure.repl]
            [clojure.java.io :as io])
  (:import [java.io PushbackInputStream]))

;; when these namespaces appear in source,
;; they will be prefixed by our pod namespace prefix
(def translate-ns?
  #{"spire.transport"})

(def pod-namespace-prefix "pod.epiccastle.")

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
  rename contains a hashmap of symbols to be renamed and what to rename them to
  ns-renames is the same for full namespaces.
  "
  [sym & [{:keys [ns-renames
                  rename
                  ]
           :or {ns-renames {}
                rename {}
                }}]]
  (binding [*print-meta* true]
    (->
     (clojure.repl/source-fn sym)
     (edamame.core/parse-string  {:all true
                                  :auto-resolve {:current (namespace sym)}})
     (rename-second rename)           ; rename the top level namespace
     (->> (clojure.walk/postwalk
           (fn [form]
             (if (symbol? form)
               (let [ns (namespace form)
                     sym-name (name form)
                     meta-data (dissoc (meta form) :row :col :end-row :end-col)]
                 (with-meta
                   (if (ns-renames ns)
                     (symbol (ns-renames ns) sym-name)

                     (if (translate-ns? ns)
                       (symbol (str pod-namespace-prefix ns) sym-name)
                       (symbol ns sym-name)
                       ))
                   meta-data))
               form))))
     prn-str)))

#_ (process-source spire.transport/ssh)
#_ (process-source spire.ssh/*piped-stream-buffer-size*)
#_ (process-source spire.transport/debug)

(defmacro make-inlined-code-set-macros
  "given a namespace, extract all the definitions for the macros
  contained therein and return a babashka pod secription hashmap
  exclude can take a set of symbols to exclude from
  processing
  "
  [namespace & [{:keys [exclude]
                 :or {exclude #{}}}]]
  (let [interns (ns-interns namespace)]
    (into []
          (filter identity
                  (for [sym (keys interns)]
                    (when (and (:macro (meta (interns sym))) ;; only process macros
                               (not (exclude sym)))
                      {"name" (str sym)
                       "code" `(process-source ~(symbol (interns sym)))
                       }))))))

#_ (make-inlined-code-set-macros spire.transport)
#_ (make-inlined-code-set-macros spire.transport {:exclude #{local ssh ssh-group}})

(defmacro make-function-lookup-table
  "given a namespace, create a hashmap table with keys of the
  function name, and values of the actual functions themselves"
  [ns]
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
  `{"name" (str pod-namespace-prefix ~(str namespace))
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

(declare lookup)



(defmacro make-lookup [ns]
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

#_ (make-lookup spire.ssh)

(def key-set (into [] "0123456789abcdefghijklmnopqrstuvwxyz"))
(def key-length 16)

(defn make-key [namespace prefix]
  (->> key-length
       range
       (map (fn [_] (rand-nth key-set)))
       (apply str prefix "-")
       (keyword namespace)))

#_ (make-key "user-info")

(defonce user-info-state (atom {}))

(def lookup
  {'pod.epiccastle.spire.ssh/make-user-info
   (fn [& args]
     (let [result (apply spire.ssh/make-user-info args)
           key (make-key "pod.epiccastle.spire.ssh" "user-info")]
       (swap! user-info-state assoc key result)
       key))

   'pod.epiccastle.spire.ssh/raw-mode-read-line
   spire.ssh/raw-mode-read-line

   })
