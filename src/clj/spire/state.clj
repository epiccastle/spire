(ns spire.state
  (:require [sci.core :as sci]
            [spire.local :as local]))

;; all the open ssh connections
;; keys => host-string
;; value => session
(defonce ssh-connections
  (atom {}))

;; the host config hashmap for the present executing context
(def host-config (sci/new-dynamic-var 'host-config nil))

;; the ssh session
(def connection (sci/new-dynamic-var 'connection nil))

;; the execution context. Used for priviledge escalation currently
(def shell-context (sci/new-dynamic-var 'shell-context nil))

;; the output module
(def output-module (sci/new-dynamic-var 'output-module nil))

(defn get-host-config []
  (if-let [conf @host-config]
    conf
    {:key "local"}))

(defn get-connection []
  (if-let [conn @connection]
    conn
    nil))

(defn get-shell-context []
  (if-let [conf @shell-context]
    conf
    {:exec :local
     :priveleges :normal
     :exec-fn local/local-exec
     :shell-fn identity
     :stdin-fn identity}))

(defn get-output-module []
  (if-let [out @output-module]
    out
    nil))
