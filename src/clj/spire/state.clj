(ns spire.state
  (:require [sci.core :as sci]
            [spire.local :as local]))

;; all the open ssh connections
;; keys => host-string
;; value => session
(defonce ssh-connections
  (atom {}))

;; the host config hashmap for the present executing context
(def host-config (sci/new-dynamic-var 'host-config {:key "local"}))

;; the ssh session
(def connection (sci/new-dynamic-var 'connection nil))

;; the execution context. Used for priviledge escalation currently
(def shell-context (sci/new-dynamic-var
                    'shell-context
                    {:exec :local
                     :exec-fn local/local-exec
                     :shell-fn identity
                     :stdin-fn identity}))

;; the output module
(def output-module (sci/new-dynamic-var 'output-module nil))

(defn get-host-config [] @host-config)
(defn get-connection [] @connection)
(defn get-shell-context [] @shell-context)
(defn get-output-module [] @output-module)
