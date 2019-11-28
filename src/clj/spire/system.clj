(ns spire.system
  (:require [clojure.java.shell :as shell]
            [spire.transport :as transport]
            )
  )

(def apt-env
  {"DEBIAN_FRONTEND" "noninteractive"})

(defn make-env []
  (into apt-env (System/getenv)))

(defmulti apt (fn [state & args] state))

(defmethod apt :update [_]
  #_(shell/sh "apt-get" "update" :env (make-env))
  (transport/sh "DEBIAN_FRONTEND=noninteractive apt-get update" "" ""))

(defmethod apt :upgrade [_]
  (shell/sh "apt-get" "upgrade" "-y" :env (make-env)))

(defmethod apt :dist-upgrade [_]
  (shell/sh "apt-get" "dist-upgrade" "-y" :env (make-env)))

(defmethod apt :autoremove [_]
  (shell/sh "apt-get" "autoremove" "-y" :env (make-env)))

(defmethod apt :clean [_]
  (shell/sh "apt-get" "clean" "-y" :env (make-env)))

(defmethod apt :install [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "install" "-y" package-or-packages :env (make-env))
    (apply shell/sh "apt-get" "install" "-y" (concat package-or-packages [:env (make-env)]))))

(defmethod apt :remove [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "remove" "-y" package-or-packages :env (make-env))
    (apply shell/sh "apt-get" "remove" "-y" (concat package-or-packages [:env (make-env)]))))

(defmethod apt :purge [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "purge" "-y" package-or-packages :env (make-env))
    (apply shell/sh "apt-get" "purge" "-y" (concat package-or-packages [:env (make-env)]))))

(defmethod apt :download [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "download" "-y" package-or-packages :env (make-env))
    (apply shell/sh "apt-get" "download" "-y" (concat package-or-packages [:env (make-env)]))))

#_ (apt :download ["iputils-ping" "traceroute"])
#_ (apt :autoremove)

(defn hostname [hostname]
  (spit "/etc/hostname" hostname)
  (shell/sh "hostname" hostname))
