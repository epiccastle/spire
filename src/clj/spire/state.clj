(ns spire.state
  (:require [spire.local :as local]))

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
(def ^:dynamic *host-config* nil)
(def ^:dynamic *connection* nil)
(def ^:dynamic *shell-context* nil)
(def ^:dynamic *output-module* nil)

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
  (if (bound? #'*host-config*)
    *host-config*
    (let [{:keys [host-config]} @default-context]
      (or host-config {:key "local"}))))

(defn get-connection []
  (if (bound? #'*connection*)
    *connection*
    (:connection @default-context)))

(defn get-shell-context []
  (if (bound? #'*shell-context*)
    *shell-context*
    (or (:shell-context @default-context)
        {:exec :local
         :privilege :normal
         :exec-fn local/local-exec})))

(defn get-output-module []
  (if (bound? #'*output-module*)
    *output-module*
    nil))
