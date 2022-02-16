(ns spire.pod.lookup
  (:require [spire.pod.mapping :as mapping]
            [spire.pod.stream]
            [spire.pod.scp]
            [spire.ssh]
            [spire.transport]
            [spire.facts]
            [spire.local]
            [spire.sudo]
            [spire.module.shell]
            [spire.module.sudo]
            ))

;; weak mappings
(def user-info-state (mapping/make-weak-mapping))
(def session-state (mapping/make-weak-mapping))
(def agent-state (mapping/make-weak-mapping))
(def channel-exec-state (mapping/make-weak-mapping))
(def piped-input-stream-state (mapping/make-weak-mapping))
(def piped-output-stream-state (mapping/make-weak-mapping))
(def channel-state (mapping/make-weak-mapping))

;; strong mappings
(def strong-piped-input-stream-state (atom (mapping/make-strong-mapping)))
(def strong-piped-output-stream-state (atom (mapping/make-strong-mapping)))

(defmacro make-plain-lookup [ns-to syms]
  (let [ns-from (str "pod.epiccastle." (str ns-to))]
    (into []
          (for [sym syms]
               `['~(symbol ns-from (str sym))
                 ~(symbol ns-to (str sym))]))))

#_
(macroexpand-1 '(make-plain-lookup "spire.foo" [a b c]))

#_(make-plain-lookup "spire.local" [is-file? is-dir?])

#_ (symbol "foo" "bar")

(def lookup
  (-> {}

      ;;
      ;; spire.ssh
      ;;
      (into
       {'pod.epiccastle.spire.ssh/make-user-info
        (fn [& args]
          (mapping/add-instance!
           user-info-state
           (apply spire.ssh/make-user-info args)
           "pod.epiccastle.spire.ssh" "user-info"))

        'pod.epiccastle.spire.ssh/make-session
        (fn [agent-key & args]
          (mapping/add-instance!
           session-state
           (apply spire.ssh/make-session (mapping/get-instance-for-key agent-state agent-key) args)
           "pod.epiccastle.spire.ssh" "session"))

        'pod.epiccastle.spire.ssh/ssh-exec-proc*
        (fn [session-key & args]
          (let [{:keys [channel out err in]}
                (apply spire.ssh/ssh-exec-proc
                       (mapping/get-instance-for-key session-state session-key)
                       args)]
            {:channel (mapping/add-instance!
                       channel-exec-state channel
                       "pod.epiccastle.spire.ssh" "channel-exec")
             :out (mapping/add-instance!
                   piped-input-stream-state out
                   "pod.epiccastle.spire.ssh" "piped-input-stream")
             :err (mapping/add-instance!
                   piped-input-stream-state err
                   "pod.epiccastle.spire.ssh" "piped-input-stream")
             :in (mapping/add-instance!
                  piped-output-stream-state in
                  "pod.epiccastle.spire.ssh" "piped-output-stream")}))

        'pod.epiccastle.spire.ssh/ssh-exec*
        (fn [session-key cmd in out opts]
          (let [{:keys [channel out-stream err-stream exit out err]}
                (spire.ssh/ssh-exec
                 (mapping/get-instance-for-key session-state session-key)
                 cmd
                 (if (keyword? in)
                   (mapping/get-instance-for-key piped-input-stream-state in)
                   in)
                 out
                 opts)]
            (if channel
              ;; streaming response
              {:channel (mapping/add-instance!
                         channel-state channel
                         "pod.epiccastle.spire.ssh" "channel")
               :out-stream (mapping/add-instance!
                            piped-input-stream-state out-stream
                            "pod.epiccastle.spire.ssh" "piped-input-stream")
               :err-stream (mapping/add-instance!
                            piped-input-stream-state err-stream
                            "pod.epiccastle.spire.ssh" "piped-input-stream")}

              ;; full response
              {:exit exit
               :out out
               :err err}
              )
            ))

        'pod.epiccastle.spire.ssh/wait-for-channel-exit
        (fn [channel]
          (spire.ssh/wait-for-channel-exit (mapping/get-instance-for-key channel-state channel)))
        })

      (into
       (make-plain-lookup
        "spire.ssh"
        [raw-mode-read-line
         print-flush-ask-yes-no
         split-host-string
         parse-host-string
         host-config-to-string
         host-config-to-connection-key
         fill-in-host-description-defaults
         host-description-to-host-config]))


      ;;
      ;; spire.transport
      ;;
      (into
       {
        'pod.epiccastle.spire.transport/make-agent
        (fn []
          (let [result (spire.transport/make-agent)]
            (mapping/add-instance!
             agent-state result
             "pod.epiccastle.spire.transport" "agent")))

        'pod.epiccastle.spire.transport/connect
        (fn [& args]
          (let [result (apply spire.transport/connect args)]
            (mapping/add-instance!
             session-state result
             "pod.epiccastle.spire.transport" "session")))

        'pod.epiccastle.spire.transport/disconnect
        (fn [& args]
          (let [connection (mapping/get-instance-for-key session-state (first args))]
            (spire.transport/disconnect connection)))

        'pod.epiccastle.spire.transport/open-connection
        (fn [& args]
          (mapping/add-instance!
           session-state (apply spire.transport/open-connection args)
           "pod.epiccastle.spire.transport" "session"))

        'pod.epiccastle.spire.transport/get-connection
        (fn [& args]
          (mapping/get-key-for-instance session-state (apply spire.transport/get-connection args)))


        })

      (into
       (make-plain-lookup
        "spire.transport"
        [disconnect-all!
         close-connection
         flush-out
         ]))

      (into
       (make-plain-lookup
        "spire.local"
        [is-file? is-dir? is-readable? is-writable?
         path-md5sums path-full-info local-exec]))

      (into
       (make-plain-lookup
        "spire.facts"
        [
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
         get-facts-id-script
         ]))

      (into
       (make-plain-lookup
        "spire.remote"
        [is-writable? is-readable? is-file? is-dir? exists?
         process-md5-out path-md5sums process-stat-mode-out
         path-full-info make-temp-filename]))

      (into
       (make-plain-lookup
        "spire.state"
        []))

      (into
       (make-plain-lookup
        "spire.output.default"
        [up down right left clear-screen-from-cursor-down
         read-until read-cursor-position elide-form-strings
         find-forms find-last-form find-form-indices
         find-last-form-index find-form-missing-hoststring-indices
         find-first-form-missing-hoststring-index
         calculate-heights state-line-complete? cut-trailing-blank-line
         update-accessible-line-count
         print-failure print-debug print-state
         count-lines state-change
         output-print-thread
         find-forms-matching-index
         output-print-form
         output-print-result
         output-debug-result
         output-print-progress
         output-print-streams
         ]))

      (into
       (make-plain-lookup
        "spire.module.apt"
        [
         preflight process-result make-script
         process-values process-apt-update-line]))

      (into
       (make-plain-lookup
        "spire.module.apt-key"
        [
         preflight make-script
         process-apt-key-list-output process-result]))

      (into
       (make-plain-lookup
        "spire.module.apt-repo"
        [
         preflight make-script
         process-result make-filename-from-repo
         ]))

      (into
       (make-plain-lookup
        "spire.module.attrs"
        [
         preflight make-script
         process-result get-mode-and-times create-attribute-list make-preserve-script
         ]))

      (into
       (make-plain-lookup
        "spire.module.authorized-keys"
        [
         process-options preflight make-script
         process-result
         ]))

      (into
       (make-plain-lookup
        "spire.module.get-file"
        [
         ]))

      (into
       (make-plain-lookup
        "spire.module.curl"
        [
         make-script
         ]))

      (into
       (make-plain-lookup
        "spire.module.group"
        [
         preflight make-script
         process-result
         ]))

      (into
       (make-plain-lookup
        "spire.module.line-in-file"
        [
         preflight make-script
         process-result escape-leading-spaces
         ]))

      (into
       (make-plain-lookup
        "spire.module.mkdir"
        [
         preflight make-script
         process-result
         ]))

      (into
       (make-plain-lookup
        "spire.module.stat"
        [
         preflight make-script
         process-result
         other-exec? other-write? other-read?
         group-exec? group-write? group-read?
         user-exec? user-write? user-read?
         mode-flags exec? readable? writeable?
         directory? block-device? char-device?
         symlink? fifo? regular-file? socket?

         ]))

       (into
       (make-plain-lookup
        "spire.module.sysctl"
        [
         preflight make-script
         process-result
         ]))

      (into
       (make-plain-lookup
        "spire.module.rm"
        [
         ]))

      (into
       (make-plain-lookup
        "spire.module.service"
        [
         preflight make-script
         process-result
         ]))

      (into
       (make-plain-lookup
        "spire.module.shell"
        [
         preflight process-result make-env-string
         make-exists-string]))

      (into
       (make-plain-lookup
        "spire.module.upload"
        [
         preflight process-result process-md5-out]))

      (into
       (make-plain-lookup
        "spire.module.download"
        [
         preflight process-result]))

      (into
       (make-plain-lookup
        "spire.sh"
        [proc feed-from feed-from-string]))

      (into
       (make-plain-lookup
        "spire.shlex"
        [parse]))

      (into
       (make-plain-lookup
        "spire.scp"
        [scp-to scp-content-to scp-parse-times scp-parse-copy
         scp-sink-file scp-sink scp-from]))

      (into
       {'pod.epiccastle.spire.scp/pod-side-streams-for-in
        (fn [buffer]
          (let [[is os] (spire.pod.scp/pod-side-streams-for-in buffer)]
            [(mapping/add-instance!
              piped-input-stream-state
              is
              "pod.epiccastle.spire.scp" "piped-input-stream")
             (mapping/add-instance!
              piped-output-stream-state
              os
              "pod.epiccastle.spire.scp" "piped-output-stream")]

            #_[(mapping/add-strong-instance!
                strong-piped-input-stream-state
                is
                "pod.epiccastle.spire.scp" "piped-input-stream")
               (mapping/add-strong-instance!
                strong-piped-output-stream-state
                os
                "pod.epiccastle.spire.scp" "piped-output-stream")]

            ))
        })

      (into
       (make-plain-lookup
        "spire.sudo"
        [make-sudo-command]))



      #_(into
         (make-plain-lookup
          "spire.pod.stream"
          [encode decode
           write-byte write-bytes
           read-byte read-bytes
           receive close available connect]))

      (into
       {
        ;;
        ;; piped input stream
        ;;
        'pod.epiccastle.spire.pod.stream/available
        (fn [stream-key]
          (spire.pod.stream/available
           (mapping/get-instance-for-key piped-input-stream-state stream-key)))

        'pod.epiccastle.spire.pod.stream/close-input-stream
        (fn [stream-key]
          (spire.pod.stream/close-input-stream
           (mapping/get-instance-for-key piped-input-stream-state stream-key)))

        'pod.epiccastle.spire.pod.stream/read-byte
        (fn [stream-key]
          (spire.pod.stream/read-byte
           (mapping/get-instance-for-key piped-input-stream-state stream-key)))

        'pod.epiccastle.spire.pod.stream/read-bytes
        (fn [stream-key length]
          (spire.pod.stream/read-bytes
           (mapping/get-instance-for-key piped-input-stream-state stream-key)
           length))

        'pod.epiccastle.spire.pod.stream/receive
        (fn [stream-key b]
          (spire.pod.stream/receive
           (mapping/get-instance-for-key piped-input-stream-state stream-key)
           b))

        ;;
        ;; piped output stream
        ;;
        'pod.epiccastle.spire.pod.stream/close-output-stream
        (fn [stream-key]
          (spire.pod.stream/close-output-stream
           (mapping/get-instance-for-key piped-output-stream-state stream-key)))

        'pod.epiccastle.spire.pod.stream/connect
        (fn [stream-key sink-key]
          (spire.pod.stream/connect
           (mapping/get-instance-for-key piped-output-stream-state stream-key)
           (mapping/get-instance-for-key piped-input-stream-state sink-key)))

        'pod.epiccastle.spire.pod.stream/flush-output-stream
        (fn [stream-key]
          (spire.pod.stream/flush-output-stream
           (mapping/get-instance-for-key piped-output-stream-state stream-key)))

        'pod.epiccastle.spire.pod.stream/write-byte
        (fn [stream-key b]
          (spire.pod.stream/write-byte
           (mapping/get-instance-for-key piped-output-stream-state stream-key)
           b))

        'pod.epiccastle.spire.pod.stream/write-bytes
        (fn [stream-key bs]
          (spire.pod.stream/write-bytes
           (mapping/get-instance-for-key piped-output-stream-state stream-key)
           bs))

        })

      (into
       (make-plain-lookup
        "spire.selmer"
        [selmer]))

      (into
       (make-plain-lookup
        "spire.utils"
        [to-snakecase
         lsb-process
         escape-code
         escape-codes
         colour
         reverse-text
         bold
         reset
         speed-string
         eta-string
         has-terminal?
         get-terminal-width
         #_progress-bar
         #_progress-bar-from-stats
         strip-colour-codes
         displayed-length
         n-spaces
         append-erasure-to-line
         num-terminal-lines
         ;;re-pattern-to-sed
         containing-folder
         path-escape
         var-escape
         double-quote
         path-quote
         string-escape
         string-quote
         changed?
         md5
         md5-file
         ]))

      (into
       (make-plain-lookup
        "spire.module.apt"
        [
         preflight process-result make-script
         process-values process-apt-update-line
         apt*
         ]))

      (into
       (make-plain-lookup
        "spire.nio"
        [relativise
         last-access-time
         last-modified-time
         file-mode
         set-file-mode
         create-file
         timestamp->touch
         timestamp->touch-bsd
         set-last-modified-time
         set-last-access-time
         set-last-modified-and-access-time
         idem-set-last-access-time
         idem-set-last-modified-time
         set-owner
         idem-set-owner
         set-group
         idem-set-group
         idem-set-mode
         set-attr
         set-attrs
         set-attrs-preserve
         ]))

      (into
       (make-plain-lookup
        "spire.compare"
        [same-files
         gather-file-sizes
         same-file-content
         add-transfer-set
         local-to-remote
         remote-to-local
         remote-to-local-dirs]))
      )


  )
