(ns spire.module.service
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.utils :as utils]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (service :restarted ...)
;;
(defmethod preflight :restarted [_ {:keys [name value reload file] :as opts}]
  nil)

(defmethod make-script :restarted [_ {:keys [value reload file] :as opts}]
  (format "service %s restart" (name (:name opts)))
  )

(defmethod process-result :restarted
  [_ {:keys [name value reload file] :as opts} {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    (assoc result
           :exit 0
           :result :changed)

    :else
    (assoc result
           :result :failed)))

;;
;; (service :stopped ...)
;;

(defmethod preflight :stopped [_ {:keys [name value reload file] :as opts}]
  nil)

(defmethod make-script :stopped [_ {:keys [value reload file] :as opts}]
  (utils/make-script
   "service_systemd_stopped.sh"
   {:NAME (some->> opts :name name utils/path-escape)}))

(defmethod process-result :stopped
  [_ {:keys [name value reload file] :as opts} {:keys [out err exit] :as result}]
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


;;
;; (service :started ...)
;;

(defmethod preflight :started [_ {:keys [name value reload file] :as opts}]
  nil)

(defmethod make-script :started [_ {:keys [value reload file] :as opts}]
  (utils/make-script
   "service_systemd_started.sh"
   {:NAME (some->> opts :name name utils/path-escape)}))

(defmethod process-result :started
  [_ {:keys [name value reload file] :as opts} {:keys [out err exit] :as result}]
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





(utils/defmodule service* [command {:keys [name value reload file] :as opts}]
  [host-string session]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts)))

  )

(defmacro service [& args]
  `(utils/wrap-report ~*file* ~&form (service* ~@args)))

(def documentation
  {
   :module "service"
   :blurb "Manage system services"
   :description
   [
    "This module manages the running of system services."
    "It can start, stop, restart and reload services."
    "It can configure a service to start on boot, or to not start on boot."]
   :form "(service command opts)"
   :args
   [{:arg "command"
     :desc "The overall command to execure. Use `:started`, `:stopped` or `:restarted`"}
    {:arg "opts"
     :desc "A hashmap of options"}]})
