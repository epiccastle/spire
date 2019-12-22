(ns spire.module.authorized-keys
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.local :as local]
            [spire.remote :as remote]
            [spire.compare :as compare]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defmethod preflight :present [_ {:keys [user state key options path] :as opts}]
  nil
  )

(defmethod make-script :present [_ {:keys [user state key options path] :as opts}]
  (utils/make-script
   "authorized_keys.sh"
   {:USER user}))

(defmethod process-result :present
  [_ {:keys [user state key options path] :as opts} {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    (assoc result
           :exit 0
           :result :ok)

    (= 255 exit)
    (assoc result
           :exit 0
           :result :changed)

    :else
    (assoc result
           :result :failed)))

(utils/defmodule authorized-keys [command {:keys [user key options path] :as opts}]
  [host-string session]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts))))
