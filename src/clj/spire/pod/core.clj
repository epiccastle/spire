(ns spire.pod.core
  (:refer-clojure :exclude [read-string read])
  (:require [bencode.core :refer [read-bencode write-bencode]]
            [spire.pod.utils :as utils]
            [spire.pod.lookup :as lookup]
            [spire.pod.stream]
            [spire.transport]
            [spire.ssh]
            [spire.pod.scp]
            [spire.shlex]
            [spire.sh]
            [spire.selmer]
            [spire.local]
            [spire.remote]
            [spire.nio]
            [spire.output.core]
            [spire.output.default]
            [spire.module.shell]
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

(def ns-renames
  {"spire.transport" "pod.epiccastle.spire.transport"
   "spire.ssh" "pod.epiccastle.spire.ssh"
   "spire.context" "pod.epiccastle.spire.context"
   "spire.local" "pod.epiccastle.spire.local"
   "spire.state" "pod.epiccastle.spire.state"
   "spire.facts" "pod.epiccastle.spire.facts"
   "spire.remote" "pod.epiccastle.spire.remote"
   "spire.utils" "pod.epiccastle.spire.utils"
   "spire.nio" "pod.epiccastle.spire.nio"
   "spire.sh" "pod.epiccastle.spire.sh"
   "spire.pod.stream" "pod.epiccastle.spire.pod.stream"
   "spire.output.core" "pod.epiccastle.spire.output.core"
   "spire.output.default" "pod.epiccastle.spire.output.default"
   "spire.module.shell" "pod.epiccastle.spire.module.shell"
   "spire.module.sudo" "pod.epiccastle.spire.module.sudo"
   "spire.sudo" "pod.epiccastle.spire.sudo"

   "transport" "pod.epiccastle.spire.transport"
   "ssh" "pod.epiccastle.spire.ssh"
   "context" "pod.epiccastle.spire.context"
   "local" "pod.epiccastle.spire.local"
   "state" "pod.epiccastle.spire.state"
   "facts" "pod.epiccastle.spire.facts"
   "utils" "pod.epiccastle.spire.utils"
   "sh" "pod.epiccastle.spire.sh"
   "shlex" "pod.epiccastle.spire.shlex"
   "nio" "pod.epiccastle.spire.nio"
   "io" "clojure.java.io"
   "output" "pod.epiccastle.spire.output.core"
   "string" "clojure.string"
   "remote" "pod.epiccastle.spire.remote"
   "PosixFilePermission" "java.nio.file.attribute.PosixFilePermission"
   })

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
                           ;;
                           ;; spire.shlex
                           ;;
                           (
                            utils/make-inlined-namespace
                            pod.epiccastle.spire.shlex
                            (utils/make-inlined-public-fns
                             spire.shlex
                             {:only #{parse}}
                             )
                            (utils/make-inlined-code-set
                             spire.shlex
                             [whitespace-chars newline-chars read-char skip-until
                              read-double-quotes read-single-quotes
                              read-until-whitespace read-while-whitespace
                              ]
                             {:rename-ns ns-renames
                              :rename-symbol {}})
                            )

                           ;;
                           ;; spire.sh
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.sh
                            (utils/make-inlined-code-set
                             spire.sh
                             [proc feed-from feed-from-string
                              *piped-stream-buffer-size*
                              streams-for-out streams-for-in read-all-bytes
                              exec
                              ]
                             {:rename-ns ns-renames
                              :rename-symbol {PipedOutputStream. java.io.PipedOutputStream.
                                              PipedInputStream. java.io.PipedInputStream.
                                              ByteArrayOutputStream. java.io.ByteArrayOutputStream.
                                              }}))

                           ;;
                           ;; spire.sudo
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.sudo

                            (utils/make-inlined-public-fns
                             spire.sudo
                             {:only #{make-sudo-command}}
                             )

                            (utils/make-inlined-code-set
                             spire.sudo
                             [copy-string-into-byte-array
                              prefix-sudo-stdin]
                             {:rename-ns ns-renames
                              :rename-symbol {PipedInputStream java.io.PipedInputStream}}
                             )

                            )


                           ;;
                           ;; spire.local
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.local
                            (utils/make-inlined-public-fns
                             spire.local
                             {:exclude #{local-exec}})

                            (utils/make-inlined-code-set
                             spire.local
                             [local-exec]
                             {:rename-ns ns-renames}
                             )
                            )


                           ;;
                           ;; spire.utils
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.utils
                            (utils/make-inlined-public-fns
                             spire.utils
                             {:exclude #{current-file current-file-parent}}
                             )
                            [{"name" "current-file"
                              "code" "(defn current-file [] *file*)"}]
                            (utils/make-inlined-code-set
                             spire.utils [current-file-parent
                                          colour-map
                                          kilobyte
                                          megabyte
                                          gigabyte
                                          clear-line

                                          ]
                             {:rename-ns ns-renames}
                             )
                            (utils/make-inlined-code-set-macros
                             spire.utils
                             {:rename-ns ns-renames})

                            [
                             {"name" "content-size"
                              "code" "
(defmulti content-size type)
(defmethod content-size java.io.File [f] (.length ^java.io.File f))
(defmethod content-size java.lang.String [f] (count (.getBytes ^String f)))
(defmethod content-size (Class/forName \"[B\") [f] (count f))
"}
                             {"name" "content-display-name"
                              "code" "
(defmulti content-display-name type)
(defmethod content-display-name java.io.File [f] (.getName ^java.io.File f))
(defmethod content-display-name java.lang.String [f] \"[String Data]\")
(defmethod content-display-name (Class/forName \"[B\") [f] \"[Byte Array]\")
"}
                             {"name" "content-recursive?"
                              "code" "
(defmulti content-recursive? type)
(defmethod content-recursive? java.io.File [f] (.isDirectory ^java.io.File f))
(defmethod content-recursive? java.lang.String [f] false)
(defmethod content-recursive? (Class/forName \"[B\") [f] false)
"}

                             {"name" "content-file?"
                              "code" "
(defmulti content-file? type)
(defmethod content-file? java.io.File [f] (.isFile ^java.io.File f))
(defmethod content-file? java.lang.String [f] false)
(defmethod content-file? (Class/forName \"[B\") [f] false)
"}

                             {"name" "content-stream"
                              "code" "
(defmulti content-stream type)
(defmethod content-stream java.io.File [f] (clojure.java.io/input-stream ^java.io.File f))
(defmethod content-stream java.lang.String [f] (clojure.java.io/input-stream (.getBytes ^String f)))
(defmethod content-stream (Class/forName \"[B\") [f] (clojure.java.io/input-stream f))
"}]

                            (utils/make-inlined-code-set
                             spire.utils
                             [progress-stats
                              progress-bar-from-stats
                              re-pattern-to-sed
                              ]
                             {:rename-ns ns-renames}
                             )
                            )

                           ;;
                           ;; output modules
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.output.core
                            (utils/make-inlined-code-set
                             spire.output.core
                             [print-thread print-form print-result debug-result print-progress print-streams]))

                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.output.default
                            [{"name" "max-string-length"
                              "code" "(def ^:dynamic max-string-length nil)"}]

                            (utils/make-inlined-public-fns
                             spire.output.default)

                            [{"name" "_multimethod_output_print-thread"
                              "code" "(defmethod pod.epiccastle.spire.output.core/print-thread :default [_] (output-print-thread))"}
                             {"name" "_multimethod_output_print-form"
                              "code" "(defmethod pod.epiccastle.spire.output.core/print-form :default [_ file form file-meta host-config] (output-print-form file form file-meta host-config))"}
                             {"name" "_multimethod_output_print-result"
                              "code" "(defmethod pod.epiccastle.spire.output.core/print-result :default [_ file form file-meta host-config result] (output-print-result file form file-meta host-config result))"}
                             {"name" "_multimethod_output_debug-result"
                              "code" "(defmethod pod.epiccastle.spire.output.core/debug-result :default [_ file form file-meta host-config result] (output-debug-result file form file-meta host-config result))"}
                             {"name" "_multimethod_output_print-progress"
                              "code" "(defmethod pod.epiccastle.spire.output.core/print-progress :default [_ file form form-meta host-string data] (output-print-progress file form form-meta host-string data))"}
                             {"name" "_multimethod_output_print-streams"
                              "code" "(defmethod pod.epiccastle.spire.output.core/print-streams :default [_ file form form-meta host-string stdout stderr] (output-print-streams file form form-meta host-string stdout stderr))"}

                             ]

                            )


                           ;;
                           ;; spire.pod.stream
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.pod.stream
                            (utils/make-inlined-public-fns
                             spire.pod.stream
                             {:exclude
                              #{encode-base64 decode-base64
                                make-piped-input-stream
                                make-piped-output-stream}})
                            (utils/make-inlined-code-set
                             spire.pod.stream
                             [encode-base64 decode-base64
                              make-piped-input-stream
                              make-piped-output-stream]
                             {:rename-ns ns-renames}
                             )
                            )


                           ;;
                           ;; spire.ssh
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.ssh
                            [#_{"name" "_imports"
                                "code" "(import [java.io
            PipedInputStream PipedOutputStream
            ByteArrayInputStream ByteArrayOutputStream
            ])"}]
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

                              streams-for-out streams-for-in string-stream
                              ]
                             {:pre-requires [[clojure.string :as string]]
                              :rename-ns ns-renames
                              :rename-symbol {PipedOutputStream. java.io.PipedOutputStream.
                                              PipedInputStream. java.io.PipedInputStream.
                                              ByteArrayOutputStream. java.io.ByteArrayOutputStream.
                                              ByteArrayInputStream. java.io.ByteArrayInputStream.}})
                            ;;(utils/make-inlined-code-set-macros spire.ssh)
                            (utils/make-inlined-public-fns
                             spire.ssh
                             {:exclude #{debug ctrl-c carridge-return default-port
                                         to-camel-case string-to-byte-array
                                         ascii utf-8
                                         *piped-stream-buffer-size*
                                         streams-for-out streams-for-in string-stream
                                         ssh-exec-proc}})

                            [{"name" "ssh-exec-proc*"}
                             {"name" "ssh-exec-proc"
                              "code" "(defn ssh-exec-proc [session cmd opts]
(let [{:keys [channel out in err]} (ssh-exec-proc* session cmd opts)]
{:channel channel
 :out (pod.epiccastle.spire.pod.stream/make-piped-input-stream out)
 :in (pod.epiccastle.spire.pod.stream/make-piped-output-stream in)
 :err (pod.epiccastle.spire.pod.stream/make-piped-input-stream err)
})
)"}
                             ;; spire side
                             {"name" "ssh-exec*"}

                             ;; bb side
                             {"name" "ssh-exec"
                              "code" "(defn ssh-exec [session cmd in out opts]
(let [{:keys [channel err-stream out-stream exit out err]} (ssh-exec* session cmd in out opts)]
(if channel {:channel channel :out-stream (pod.epiccastle.spire.pod.stream/make-piped-input-stream out-stream) :err-stream (pod.epiccastle.spire.pod.stream/make-piped-input-stream err-stream)} {:exit exit :out out :err err})))
"}

                             ]
                            )

                           ;;
                           ;; spire.transport
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.transport
                            (utils/make-inlined-code-set
                             spire.transport
                             [debug]
                             {:rename-ns ns-renames})
                            (utils/make-inlined-public-fns spire.transport)
                            (utils/make-inlined-code-set-macros
                             spire.transport
                             {:rename-symbol
                              {open-connection pod.epiccastle.spire.transport/open-connection
                               close-connection pod.epiccastle.spire.transport/close-connection
                               }
                              :rename-ns ns-renames})
                            )

                           ;;
                           ;; spire.context
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.context
                            [{"name" "context"
                              "code" "(def ^:dynamic context :babashka)"}]
                            (utils/make-inlined-code-set
                             spire.context
                             [binding-sym deref-sym]
                             {:rename-ns ns-renames})

                            ;;(utils/make-inlined-public-fns spire.context)
                            (utils/make-inlined-code-set-macros
                             spire.context
                             {:rename-ns ns-renames}))


                           ;;
                           ;; spire.state
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.state

                            [{"name" "host-config"
                              "code" "(def ^:dynamic host-config nil)"}

                             {"name" "connection"
                              "code" "(def ^:dynamic connection nil)"}

                             {"name" "shell-context"
                              "code" "(def ^:dynamic shell-context nil)"}

                             {"name" "output-module"
                              "code" "(def ^:dynamic output-module nil)"}
                             ]

                            (utils/make-inlined-code-set
                             spire.state
                             [ssh-connections
                              default-context
                              set-default-context!
                              get-default-context
                              get-host-config
                              get-connection
                              get-shell-context
                              get-output-module
                              ]
                             {:rename-ns ns-renames})

                            (utils/make-inlined-public-fns
                             spire.state
                             {:exclude
                              #{
                                host-config
                                connection
                                shell-context
                                output-module
                                set-default-context!
                                get-default-context
                                get-host-config
                                get-connection
                                get-shell-context
                                get-output-module}}))

                           ;;
                           ;; spire.facts
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.facts

                            (utils/make-inlined-public-fns
                             spire.facts
                             {:only
                              #{
                                process-uname
                                process-shell-uname
                                process-shell-info
                                process-system
                                process-paths
                                process-lsb-release
                                guess-mac-codename
                                process-system-profiler
                                process-id-name-substring
                                process-id
                                get-facts-fish-script
                                get-facts-csh-script
                                get-facts-sh-script
                                get-facts-id-script}})

                            (utils/make-inlined-code-set
                             spire.facts
                             [state
                              bins
                              make-which
                              mac-codenames
                              runner
                              fetch-shell
                              fetch-shell-facts
                              fetch-facts
                              run-and-return-lines
                              run-lsb-release
                              run-system-profiler
                              process-release-info
                              fetch-shell-facts-fish
                              fetch-shell-facts-csh
                              fetch-shell-facts-default
                              update-facts!
                              get-fact
                              fetch-facts-paths
                              update-facts-paths!
                              update-facts-user!
                              replace-facts-user!
                              os
                              md5
                              check-bins-present]
                             {:rename-ns ns-renames
                              :rename-symbol
                              { ;;fetch-facts pod.epiccastle.spire.facts/fetch-facts
                               process-lsb-release pod.epiccastle.spire.facts/process-lsb-release
                               }})

                            [
                             {"name" "_multimethod_facts_fetch-shell-facts-fish"
                              "code" "(defmethod pod.epiccastle.spire.facts/fetch-shell-facts :fish [shell] (fetch-shell-facts-fish shell))"}

                             {"name" "_multimethod_facts_fetch-shell-facts-csh"
                              "code" "(defmethod pod.epiccastle.spire.facts/fetch-shell-facts :csh [shell] (fetch-shell-facts-csh shell))"}

                             {"name" "_multimethod_facts_fetch-shell-facts-default"
                              "code" "(defmethod pod.epiccastle.spire.facts/fetch-shell-facts :default [shell] (fetch-shell-facts-default shell))"}]

                            (utils/make-inlined-code-set-macros
                             spire.facts
                             {:rename-ns ns-renames
                              :rename-symbol
                              {get-fact pod.epiccastle.spire.facts/get-fact}}))

                           ;;
                           ;; spire.module.sudo
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.module.sudo

                            (utils/make-inlined-code-set
                             spire.module.sudo
                             [passwords])

                            (utils/make-inlined-public-fns
                             spire.module.sudo
                             {:exclude #{passwords}})

                            (utils/make-inlined-code-set
                             spire.module.sudo
                             [requires-password?
                              sudo-id]
                             {:rename-ns ns-renames
                              :rename-symbol {}})

                            (utils/make-inlined-code-set-macros
                             spire.module.sudo
                             {:rename-ns ns-renames
                              :rename-symbol
                              {requires-password? pod.epiccastle.spire.module.sudo/requires-password?
                               passwords pod.epiccastle.spire.module.sudo/passwords
                               sudo-id pod.epiccastle.spire.module.sudo/sudo-id}}
                             )

                            )

                           ;;
                           ;; spire.nio
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.nio

                            (utils/make-inlined-public-fns
                             spire.nio
                             {:exclude
                              #{mode->permissions}})

                            ;; spire side multimethods
                            [{"name" "set-owner"}
                             {"name" "set-group"}]

                            (utils/make-inlined-code-set
                             spire.nio
                             [permission->mode
                              mode->permissions]
                             {:rename-ns ns-renames}))

                           ;;
                           ;; spire.remote
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.remote

                            (utils/make-inlined-public-fns
                             spire.remote
                             {:only
                              #{
                                process-md5-out
                                process-stat-mode-out
                                make-temp-filename}})

                            (utils/make-inlined-code-set
                             spire.remote
                             [is-writable? is-readable? is-file? is-dir? exists?
                              path-full-info path-md5sums]
                             {:rename-ns ns-renames}
                             )

                            #_(utils/make-inlined-public-fns spire.remote)
                            )




                           #_(utils/make-inlined-namespace
                              pod.epiccastle.spire.module.shell
                              (utils/make-inlined-public-fns spire.module.shell)
                              (utils/make-inlined-code-set-macros
                               spire.module.shell
                               {:rename-ns ns-renames
                                :rename-symbol {shell* pod.epiccastle.spire.module.shell/shell*}}
                               )
                              )

                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.module.shell
                            (utils/make-inlined-public-fns spire.module.shell)
                            (utils/make-inlined-code-set-macros
                             spire.module.shell
                             {:rename-ns ns-renames}
                             )
                            #_(utils/make-inlined-code-set
                               spire.module.shell
                               [shell*]
                               {:rename-ns ns-renames})
                            )


                           ;;
                           ;; spire.scp
                           ;;
                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.scp
                            (utils/make-inlined-public-fns spire.pod.scp)
                            [{"name" "_import"
                              "code" "(def _import (import [java.io
            InputStream OutputStream File FileOutputStream
            PipedInputStream PipedOutputStream StringReader]
           ))"}]
                            (utils/make-inlined-code-set
                             spire.pod.scp
                             [debug debugf
                              scp-send-ack
                              scp-receive-ack
                              scp-send-command
                              scp-copy-file
                              scp-copy-data
                              scp-copy-dir
                              scp-files
                              scp-to
                              scp-content-to
                              ;; scp-parse-times
                              ;; scp-parse-copy
                              scp-receive-command
                              scp-sink-file
                              scp-sink
                              scp-from
                              ]
                             {:rename-ns ns-renames
                              :rename-symbol {OutputStream java.io.OutputStream
                                              FileOutputStream java.io.FileOutputStream
                                              InputStream java.io.InputStream
                                              PipedInputStream java.io.PipedInputStream
                                              PipedOutputStream java.io.PipedOutputStream
                                              StringReader java.io.StringReader
                                              }})
                            )

                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.selmer
                            (utils/make-inlined-public-fns spire.selmer))

                           (utils/make-inlined-namespace
                            pod.epiccastle.spire.module.apt
                            (utils/make-inlined-public-fns spire.module.apt)
                            (utils/make-inlined-code-set-macros
                             spire.module.apt
                             {:rename-ns ns-renames
                              :rename-symbol {apt* pod.epiccastle.spire.module.apt/apt*}}
                             )
                            )]
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
                    (if-let [f (lookup/lookup var)]
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