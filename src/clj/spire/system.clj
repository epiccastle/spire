(ns spire.system
  (:require [clojure.java.shell :as shell])
  )

(defmulti apt (fn [state & args] state))

(defmethod apt :update [_]
  (shell/sh "apt-get" "update"))

(defmethod apt :upgrade [_]
  (shell/sh "apt-get" "upgrade" "-y"))

(defmethod apt :dist-upgrade [_]
  (shell/sh "apt-get" "dist-upgrade" "-y"))

(defmethod apt :autoremove [_]
  (shell/sh "apt-get" "autoremove" "-y"))

(defmethod apt :clean [_]
  (shell/sh "apt-get" "clean" "-y"))

(defmethod apt :install [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "install" "-y" package-or-packages)
    (apply shell/sh "apt-get" "install" "-y" package-or-packages)))

(defmethod apt :remove [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "remove" "-y" package-or-packages)
    (apply shell/sh "apt-get" "remove" "-y" package-or-packages)))

(defmethod apt :purge [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "purge" "-y" package-or-packages)
    (apply shell/sh "apt-get" "purge" "-y" package-or-packages)))

(defmethod apt :download [_ package-or-packages]
  (if (string? package-or-packages)
    (shell/sh "apt-get" "download" "-y" package-or-packages)
    (apply shell/sh "apt-get" "download" "-y" package-or-packages)))

#_ (apt :download ["iputils-ping" "traceroute"])
#_ (apt :autoremove)

(defn hostname [hostname]
  (spit "/etc/hostname" hostname)
  (shell/sh "hostname" hostname))
