(ns spire.pod.core
  (:refer-clojure :exclude [read-string read])
  (:require [bencode.core :refer [read-bencode write-bencode]]
            [spire.pod.utils :as utils]
            [spire.transport]
            [spire.ssh]
            [clojure.edn :as edn]
            [clojure.repl]
            [clojure.java.io :as io])
  (:import [java.io PushbackInputStream]
           [java.net ServerSocket]))

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

(defn write [out v]
  (write-bencode out v)
  ;;(.flush System/out)
  )

(defn read [in]
  (read-bencode in))

(defn safe-read [d]
  (when d
    (read-string d)))

(defn main []
  (let [server (ServerSocket. 0)
        port (.getLocalPort server)
        pid (.pid (java.lang.ProcessHandle/current))
        port-file (io/file (str ".babashka-pod-" pid ".port"))
        _ (.addShutdownHook (Runtime/getRuntime)
                            (Thread. (fn []
                                       (.delete port-file))))
        _ (spit port-file
                (str port "\n"))
        socket (.accept server)
        in (PushbackInputStream. (.getInputStream socket))
        out (.getOutputStream socket)
        ]
    (try
      (loop []
        (let [{:strs [id op var args ns]} (read in)
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
              (write out {"port" port
                          "format" "edn"
                          "namespaces"
                          [
                           (utils/make-inlined-namespace
                            spire.ssh
                            (utils/make-inlined-code-set
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
                            ;;(utils/make-inlined-code-set-macros spire.ssh)
                            (utils/make-inlined-public-fns
                               spire.ssh
                               {:exclude #{debug ctrl-c carridge-return default-port
                                           to-camel-case string-to-byte-array
                                           ascii utf-8
                                           *piped-stream-buffer-size*}}
                               )


                            )

                           (utils/make-inlined-namespace
                              spire.transport
                              (utils/make-inlined-code-set-macros spire.transport)
                              (utils/make-inlined-public-fns spire.transport))
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
                      args args-decoded]
                  (debug 'invoke var args)
                  (let [args (edn/read-string args)]
                    (if-let [f (utils/lookup var)]
                      (let [value (pr-str (apply f args))
                            reply {"value" value
                                   "id" id
                                   "status" ["done"]}]
                        (debug 'reply reply)
                        (write out reply))
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
                    (write out reply))))
              (recur))
            (do
              (write out {"err" (str "unknown op:" (name op))})
              (recur)))))
      (catch java.io.EOFException _ nil))))
