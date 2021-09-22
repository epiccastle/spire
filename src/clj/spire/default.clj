(ns spire.default
  (:refer-clojure :exclude [pop!])
  (:require [spire.state :as state]
            [spire.ssh :as ssh]
            [spire.transport :as transport]))

;; some routines to ease use from the nrepl. not intended for
;; script use. Use the relevant context macros instead.

(def connection-stack (atom []))

(defn pop!
  "Pop the default connection context stack and return to the previous
  context.
  (pop!)
  "
  []
  (let [[old new] (swap-vals! connection-stack
                              (fn [s]
                                (if (empty? s) s (clojure.core/pop s))))]
    (when (last old)
      (transport/close-connection (last old)))
    (if (empty? new)
      (do
        (state/set-default-context! nil nil nil)
        nil)
      (let [host-config (last new)]
        (if (nil? host-config)
          (state/set-default-context! nil nil nil)
          (state/set-default-context!
           host-config
           (transport/get-connection (ssh/host-config-to-connection-key host-config))
           {:privilege :normal
            :exec :ssh
            :exec-fn ssh/ssh-exec
            ;;:shell-fn identity
            ;;:stdin-fn identity
            }))
        true))))

(defn empty!
  "Empty the context stack and return to a local connection context as
  default.
  (empty!)
  "
  []
  (while (pop!)))


(defn push-ssh!
  "adds a new ssh connection context to the default connection stack.
  (push-ssh! host-config)

  given:

  `host-config`: a host-string or host config hashmap"
  [host-string-or-config]
  (let [host-config (ssh/host-description-to-host-config host-string-or-config)
        conn (transport/open-connection host-config)]
    (swap! connection-stack conj host-config)
    (state/set-default-context!
     host-config
     conn
     {:privilege :normal
      :exec :ssh
      :exec-fn ssh/ssh-exec
      ;;:shell-fn identity
      ;;:stdin-fn identity
      })
    true))

(defn set-ssh!
  "sets the current default connection context to the specified ssh
  connection
  (set-ssh! host-config)

  given:

  `host-config`: a host-string or host config hashmap"
  [host-string-or-config]
  (pop!)
  (push-ssh! host-string-or-config))

(defn push-local!
  "adds a new local connection context to the default connection stack
  (push-local!)
  "
  []
  (swap! connection-stack conj nil)
  (state/set-default-context! nil nil nil)
  true)

(defn set-local!
  "sets the current default connection context to the local context.
  (set-local!)"
  []
  (pop!)
  (push-local!))
