(ns spire.default
  (:refer-clojure :exclude [pop empty])
  (:require [spire.state :as state]
            [spire.ssh :as ssh]
            [spire.transport :as transport]))

;; some routines to ease use from the nrepl. not intended for
;; script use. Use the relevant context macros instead.

(def connection-stack (atom []))

(defn ssh
  "adds a new ssh connection context to the default connection stack.
  (ssh host-config)

  given:

  `host-config`: a host-string or host config hashmap"
  [host-string-or-config]
  (let [host-config (ssh/host-description-to-host-config host-string-or-config)]
    (let [conn (transport/open-connection host-config)]
      (swap! connection-stack conj host-config)
      (state/set-default-context!
       host-config
       conn
       {:priveleges :normal
        :exec :ssh
        :exec-fn ssh/ssh-exec
        :shell-fn identity
        :stdin-fn identity})
      true)))

(defn local
  "adds a new local connection context to the default connection stack
  (local)
  "
  []
  (swap! connection-stack conj nil)
  (state/set-default-context! nil nil nil)
  true)

(defn pop
  "Pop the default connection context stack and return to the previous
  context.
  (pop)
  "
  []
  (let [[old new] (swap-vals! connection-stack clojure.core/pop)]
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
           {:priveleges :normal
            :exec :ssh
            :exec-fn ssh/ssh-exec
            :shell-fn identity
            :stdin-fn identity}))
        true))))

(defn empty
  "Empty the context stack and return to a local connection context as
  default."
  []
  (while (pop)))
