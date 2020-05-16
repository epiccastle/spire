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

;; When nothing else is set in the dynamic thread locals, we use
;; the default context settings.
(def default-context (atom nil))

(defn set-default-context! [host-config connection shell-context]
  (reset! default-context
          {:host-config host-config
           :connection connection
           :shell-context shell-context}))



(defn get-default-context []
  @default-context)

(defn get-host-config []
  (if-let [conf @host-config]
    conf
    (let [{:keys [host-config]} @default-context]
      (if host-config
        host-config
        {:key "local"}))))

(defn get-connection []
  (if-let [conn @connection]
    conn
    (let [{:keys [connection]} @default-context]
      (if connection
        connection
        nil))))

(defn get-shell-context []
  (if-let [conf @shell-context]
    conf
    (let [{:keys [shell-context]} @default-context]
      (if shell-context
        shell-context
        {:exec :local
         :priveleges :normal
         :exec-fn local/local-exec
         :shell-fn identity
         :stdin-fn identity}))))

(defn get-output-module []
  (if-let [out @output-module]
    out
    nil))
