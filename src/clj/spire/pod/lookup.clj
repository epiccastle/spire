(ns spire.pod.lookup
  (:require [spire.pod.mapping :as mapping]
            [spire.ssh]
            [spire.transport]
            [spire.facts]
            [spire.local]
            [spire.module.shell]
            ))

(def user-info-state (mapping/make-mapping))
(def session-state (mapping/make-mapping))

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
  (->
   {'pod.epiccastle.spire.ssh/make-user-info
    (fn [& args]
      (mapping/add-instance!
       user-info-state (apply spire.ssh/make-user-info args)
       "pod.epiccastle.spire.ssh" "user-info"))

    'pod.epiccastle.spire.ssh/raw-mode-read-line
    spire.ssh/raw-mode-read-line

    'pod.epiccastle.spire.ssh/print-flush-ask-yes-no
    spire.ssh/print-flush-ask-yes-no

    'pod.epiccastle.spire.ssh/host-description-to-host-config
    spire.ssh/host-description-to-host-config



    ;;'pod.epiccastle.spire.ssh/make-session
    ;;spire.ssh/make-session

    ;; 'pod.epiccastle.spire.ssh/ssh-exec-proc
    ;; spire.ssh/ssh-exec-proc




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

    'pod.epiccastle.spire.transport/disconnect-all!
    spire.transport/disconnect-all!

    'pod.epiccastle.spire.transport/open-connection
    (fn [& args]
      (mapping/add-instance!
       session-state (apply spire.transport/open-connection args)
       "pod.epiccastle.spire.transport" "session"))

    'pod.epiccastle.spire.transport/close-connection
    spire.transport/close-connection

    'pod.epiccastle.spire.transport/get-connection
    (fn [& args]
      (mapping/get-key-for-instance session-state (apply spire.transport/get-connection args)))

    'pod.epiccastle.spire.transport/flush-out
    spire.transport/flush-out

    }

   (into
    (make-plain-lookup
     "spire.local"
     [is-file? is-dir? is-readable? is-writable?
      path-md5sums path-full-info local-exec]))

   (into
    (make-plain-lookup
     "spire.facts"
     [make-which process-uname process-shell-uname process-shell-info
      process-system process-paths process-lsb-release guess-mac-codename
      process-system-profiler runner fetch-shell fetch-facts run-and-return-lines
      run-lsb-release run-system-profiler process-release-info
      process-id fetch-shell-facts update-facts! get-fact fetch-facts-paths
      update-facts-paths! update-facts-user! replace-facts-user!
      os md5 check-bins-present]))

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
     "spire.module.shell"
     [
      preflight process-result make-env-string
      make-exists-string
      read-avail-string-from-input-stream
      read-stream-until-eof
      process-streams
      shell*
      ]))

   (into
    (make-plain-lookup
     "spire.selmer"
     [selmer])))
  )
