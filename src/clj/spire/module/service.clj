(ns spire.module.service
  (:require [spire.facts :as facts]
            [spire.ssh :as ssh]
            [spire.utils :as utils]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defmethod preflight :default [_ opts]
  (facts/on-os
   :freebsd (facts/check-bins-present #{:bash :service :awk})
   :linux (facts/check-bins-present #{:bash :service :grep :awk})
   ))

;;
;; (service :restarted ...)
;;

(defmethod make-script :restarted [_ opts]
  (facts/on-os
   :freebsd (format "service %s onerestart" (name (:name opts)))
   :else (format "service %s restart" (name (:name opts))))
  )

(defmethod process-result :restarted
  [_ opts {:keys [out err exit] :as result}]
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
(defmethod make-script :stopped [_ opts]
  (facts/on-os
   :linux (utils/make-script
           "service_systemd_stopped.sh"
           {:NAME (some->> opts :name name utils/path-escape)})
   :freebsd (utils/make-script
             "service_freebsd_stopped.sh"
             {:NAME (some->> opts :name name utils/path-escape)})))

(defmethod process-result :stopped
  [_ opts {:keys [out err exit] :as result}]
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
(defmethod make-script :started [_ opts]
  (facts/on-os
   :linux (utils/make-script
           "service_systemd_started.sh"
           {:NAME (some->> opts :name name utils/path-escape)})
   :freebsd (utils/make-script
             "service_freebsd_started.sh"
             {:NAME (some->> opts :name name utils/path-escape)})))

(defmethod process-result :started
  [_ opts {:keys [out err exit] :as result}]
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





(utils/defmodule service* [command opts]
  [host-string session {:keys [exec-fn sudo] :as shell-context}]
  (or
   (preflight command opts)
   (->>
    (exec-fn session "bash" (make-script command opts) "UTF-8" {:sudo sudo})
    (process-result command opts)))

  )

(defmacro service
  "manage the running of system services. Start, stop, restart and
  reload services.
  (service command opts)

  given:

  `command`: Overall command to execute. Should be `:started`,
  `:stopped` or `:restarted`.

  `opts`: A hashmap of options, where:

  `:name` the name of the service.
  "
  [& args]
  `(utils/wrap-report ~&form (service* ~@args)))

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
     :desc "The overall command to execute. Use `:started`, `:stopped` or `:restarted`"}
    {:arg "opts"
     :desc "A hashmap of options"}]

   :opts
   [
    [:name
     {:description ["The name of the service"]
      :type :string
      :required true}]]})
