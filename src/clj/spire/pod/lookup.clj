(ns spire.pod.lookup
  (:require [spire.pod.mapping :as mapping]
            [spire.ssh]
            [spire.transport]
            [spire.facts]
            ))

(def user-info-state (mapping/make-mapping))
(def session-state (mapping/make-mapping))

(def lookup
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



   'pod.epiccastle.spire.facts/update-facts!
   spire.facts/update-facts!

   'pod.epiccastle.spire.facts/get-fact
   spire.facts/get-fact





   })
