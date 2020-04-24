(ns spire.module.group
  (:require [spire.utils :as utils]
            [spire.facts :as facts]
            [spire.ssh :as ssh]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defmethod preflight :present [_ {:keys [name gid] :as opts}]
  (facts/check-bins-present #{:bash :groupadd :groupmod :grep})
  )

(defmethod make-script :present [_ {:keys [name gid password] :as opts}]
  (utils/make-script
   "group_present.sh"
   {:NAME name
    :GROUP_ID gid
    :PASSWORD password}))

(defmethod process-result :present
  [_ {:keys [user] :as opts} {:keys [out err exit] :as result}]
  (let [result (assoc result
                      :out-lines (string/split-lines out))]
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

(defmethod preflight :absent [_ {:keys [name gid system] :as opts}]
  (facts/check-bins-present #{:bash :groupdel :grep})
  )

(defmethod make-script :absent [_ {:keys [name] :as opts}]
  (utils/make-script
   "group_absent.sh"
   {:NAME name}))

(defmethod process-result :absent
  [_ {:keys [user] :as opts} {:keys [out err exit] :as result}]
  (let [result (assoc result
                      :out-lines (string/split-lines out))]
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

(utils/defmodule group* [command opts]
  [host-string session {:keys [shell-fn stdin-fn] :as shell-context}]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (shell-fn "bash") (stdin-fn (make-script command opts)) "UTF-8" {})
    (process-result command opts))))

(defmacro group [& args]
  `(utils/wrap-report ~&form (group* ~@args)))

(def documentation
  {:module "group"
   :blurb "Manage the system groups"
   :description
   ["This module ensures a particular group is present on, or absent from, the remote machine."]
   :form "(group command options)"
   :args
   [{:arg "command"
     :desc "The state the group should be in. Should be one of `:present` or `:absent`."
     :values
     [[:present "Ensures the named group is present and has the specified settings"]
      [:absent "Ensures the named group is absent"]]}
    {:arg "options"
     :desc "A hashmap of options. All available option keys and their values are described below"}]

   :opts
   [
    [:name
     {:description ["The name of the group"]
      :type :string
      :required true}]

    [:gid
     {:description ["The group id number of the named group"]
      :type :integer
      :required false}]

    [:password
     {:description ["A global password for the group."]
      :type :string
      :required false}]]})
