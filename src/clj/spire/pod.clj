(ns spire.pod
  (:refer-clojure :exclude [read-string])
  (:require [bencode.core :refer [read-bencode write-bencode]]
            [spire.transport]
            [clojure.edn :as edn]
            [clojure.repl]
            [clojure.java.io :as io])
  (:import [java.io PushbackInputStream]))

(set! *warn-on-reflection* true)

(defn read-string [^"[B" v]
  (String. v))

(def debug? true)
(def debug-file "/tmp/spire-pod-debug.txt")

(defmacro debug [& args]
  (if debug?
    `(with-open [wrtr# (io/writer debug-file :append true)]
       (.write wrtr# (prn-str ~@args)))
    nil))

(def stdin (PushbackInputStream. System/in))

(defn write [v]
  (write-bencode System/out v)
  (.flush System/out))

(defn safe-read [d]
  (when d
    (read-string d)))

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
  [sym & [{:keys [ns-renames rename]
           :or {ns-renames {}
                rename {}
                }}]]
  (->
   (clojure.repl/source-fn sym)
   (edamame.core/parse-string  {:all true
                                :auto-resolve {:current (namespace sym)}})
   (rename-second rename)               ; rename the top level namespace
   (->> (clojure.walk/postwalk
         (fn [form]
           (if (symbol? form)
             (let [ns (namespace form)
                   sym-name (name form)]
               (if (ns-renames ns)
                 (symbol (ns-renames ns) sym-name)

                 (if (translate-ns? ns)
                   (symbol (str pod-namespace-prefix ns) sym-name)
                   (symbol ns sym-name)
                   )))
             form))))
   prn-str))

#_ (process-source spire.transport/ssh)

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





(defn main []
  (try
    (loop []
      (let [{:strs [id op var args ns]} (read-bencode stdin)
            id-decoded (safe-read id)
            op-decoded (safe-read op)
            var-decoded (safe-read var)
            args-decoded (safe-read args)
            ns-decoded (safe-read ns)]
        (debug 'id id-decoded
               'op op-decoded
               'var var-decoded
               'args args-decoded
               'ns ns-decoded)
        (case op-decoded
          "describe"
          (do
            (debug 'returning (make-inlined-code-set-macros spire.transport))
            (write {"format" "edn"
                    "namespaces"
                    [{
                       "name" "spire.transport"
                      "vars" [{"name" "ssh"
                               "code" "(defn ssh [t] t)"
                               }]
                       }]
                    "id" (read-string id)})
            (recur))
          "load-ns"
          (do
            (prn "load-ns")
            (recur)
            )
          "invoke"
          (do
            (prn "invoke")
            (recur))
          (do
            (write {"err" (str "unknown op:" (name op))})
            (recur)))))
    (catch java.io.EOFException _ nil)))
