(ns spire.module.sysctl
  (:require [spire.facts :as facts]
            [spire.ssh :as ssh]
            [spire.utils :as utils]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (sysctl :present ...)
;;
(defmethod preflight :present [_ {:keys [name value reload file] :as opts}]
  (facts/check-bins-present #{:sed :sysctl :head :bash}))

(defmethod make-script :present [_ {:keys [value reload file] :as opts}]
  (utils/make-script
   "sysctl_present.sh"
   {:FILE (or file "/etc/sysctl.conf")
    :REGEX (format "^%s\\s*=" (:name opts))
    :NAME (:name opts)
    :VALUE (name value)
    :RELOAD (str reload)}))

(defmethod process-result :present
  [_ {:keys [name value reload file] :as opts} {:keys [out err exit] :as result}]
  (let [result (assoc result :out-lines (string/split out #"\n"))]
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
             :result :failed))))

;;
;; (sysctl :absent ...)
;;
(defmethod preflight :absent [_ {:keys [name value reload file] :as opts}]
  (facts/check-bins-present #{:sed :sysctl :head :bash}))

(defmethod make-script :absent [_ {:keys [value reload file] :as opts}]
  (utils/make-script
   "sysctl_absent.sh"
   {:FILE (or file "/etc/sysctl.conf")
    :REGEX (format "^%s\\s*=" (:name opts))
    :NAME (:name opts)
    :VALUE (some-> value name)
    :RELOAD (str reload)}))

(defmethod process-result :absent
  [_ {:keys [name value reload file] :as opts} {:keys [out err exit] :as result}]
  (let [result (assoc result :out-lines (string/split out #"\n"))]
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
             :result :failed))))


(utils/defmodule sysctl* [command {:keys [name value reload file] :as opts}]
  [host-string session {:keys [exec-fn sudo] :as shell-context}]
  (or
   (preflight command opts)
   (->>
    (exec-fn session "bash" (make-script command opts) "UTF-8" {:sudo sudo})
    (process-result command opts))))

(defmacro sysctl
  "manage the kernel system control parameters.
  (service command opts)

  `command`: The overall command to execute. Should be `:present` or
  `:absent`

  `opts`: a hashmap of options with the following keys:

  `:name` The name of the sysctl parameter. eg \"net.ipv4.ip_forward\"

  `:value` The value to give the parameter.

  `:reload` Should all the values be reloaded after setting

  `:file` Use a custom file to store the settings. Defaults to the
  operating system's default location.

  "
  [& args]
  `(utils/wrap-report ~&form (sysctl* ~@args)))

(def documentation
  {
   :module "sysctl"
   :blurb "Manage the kernel system control parameters."
   :description
   [
    "This module manages the configurable parameters of the running OS kernel."]
   :form "(service command opts)"
   :args
   [{:arg "command"
     :desc "The overall command to execute. Only `:present` is implemented."}
    {:arg "opts"
     :desc "A hashmap of options"}]})
