(ns spire.pod.core
  (:refer-clojure :exclude [read-string])
  (:require [bencode.core :refer [read-bencode write-bencode]]
            [spire.transport]
            [spire.ssh]
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
            (write {"format" "edn"
                    "namespaces"
                    [
                     (make-inlined-namespace
                      spire.ssh
                      (make-inlined-code-set
                       spire.ssh
                       [
                        ;; defs
                        debug ctrl-c carridge-return default-port

                        ;; privates
                        to-camel-case string-to-byte-array

                        ;; charsets? java heap?
                        ascii utf-8

                        ;; dynamic
                        *piped-stream-buffer-size*
                        ]
                       {:pre-requires [[clojure.string :as string]]}
                       )
                      ;;(make-inlined-code-set-macros spire.ssh)
                      #_(make-inlined-public-fns
                       spire.ssh
                       {:exclude #{debug}}
                       )
                      [{"name" "make-user-info"}
                       {"name" "raw-mode-read-line"
                        "code" "(defn raw-mode-read-line [] (String. (.readPassword (System/console))))"}]

                      )

                     #_(make-inlined-namespace
                        spire.transport
                        (make-inlined-code-set-macros spire.transport)
                        (make-inlined-public-fns spire.transport))
                     ]
                    "id" (read-string id)})
            (recur))
          "load-ns"
          (do
            ;;(prn "load-ns")
            (recur)
            )
          "invoke"
          (do
            (try
              (let [var (-> var
                            read-string
                            symbol)
                    args (read-string args)]
                (debug 'invoke var args)
                (let [args (edn/read-string args)]
                  (if-let [f (lookup var)]
                    (let [value (pr-str (apply f args))
                          reply {"value" value
                                 "id" id
                                 "status" ["done"]}]
                      (debug 'reply reply)
                      (write reply))
                    (throw (ex-info (str "Var not found: " var) {})))))
              (catch Throwable e
                (binding [*out* *err*]
                  (println e))
                (let [reply {"ex-message" (.getMessage e)
                             "ex-data" (pr-str
                                        (assoc (ex-data e)
                                               :type (class e)))
                             "id" id
                             "status" ["done" "error"]}]
                  (write reply))))
            (recur))
          (do
            (write {"err" (str "unknown op:" (name op))})
            (recur)))))
    (catch java.io.EOFException _ nil)))
