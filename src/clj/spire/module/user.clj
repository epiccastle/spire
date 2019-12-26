(ns spire.module.user
  (:require [spire.utils :as utils]
            [spire.ssh :as ssh]
            [spire.output :as output]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defmethod preflight :present [_ {:keys [user] :as opts}]
  nil
  )

(defmethod make-script :present [_ {:keys [name comment uid home
                                           group groups password shell
                                           ] :as opts}]
  (utils/make-script
   "user_present.sh"
   {:NAME name
    :COMMENT comment
    :USER_ID uid
    :HOME home
    :GROUP group
    :GROUPSET groups
    :PASSWORD password
    :SHELL shell}))

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

(utils/defmodule user [command {:keys [user] :as opts}]
  [host-string session]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts))))
