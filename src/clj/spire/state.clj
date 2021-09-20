(ns spire.state
  (:require [sci.core :as sci]
            [spire.local :as local]
            [spire.context :as context]))

;; ssh-connections:
;; Atom that holds all the open ssh connections. Atom value is a hashmap

;; keys => host config hash-map.
;; eg. {:username "crispin",
;;      :hostname "localhost",
;;      :port 22}
;; value => sessions and use-counts
;; eg. {:connection #object[com.jcraft.jsch.Session 0x79130491 "com.jcraft.jsch.Session@79130491"],
;;      :use-count 1}
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
  (if-let [conf (context/deref* host-config)]
    conf
    (let [{:keys [host-config]} @default-context]
      (if host-config
        host-config
        {:key "local"}))))

(defn get-connection []
  (if-let [conn (context/deref* connection)]
    conn
    (let [{:keys [connection]} @default-context]
      (if connection
        connection
        nil))))

(defn get-shell-context []
  (if-let [conf (context/deref* shell-context)]
    conf
    (let [{:keys [shell-context]} @default-context]
      (if shell-context
        shell-context
        {:exec :local
         :privilege :normal
         :exec-fn local/local-exec
         :shell-fn identity
         :stdin-fn identity}))))

(defn get-output-module []
  (if-let [out (context/deref* output-module)]
    out
    nil))
