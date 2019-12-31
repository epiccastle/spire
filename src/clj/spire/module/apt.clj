(ns spire.module.apt
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]))

;; defaults
(def options-match-choices #{:first :last :all})
(def options-match-default :first)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (line-in-file :present ...)
;;
(defmethod preflight :install [_ _]
  (when-not (facts/get-fact [:paths :apt-get])
    {:exit 1
     :out ""
     :err "apt module requires apt-get installed and present in the path."
     :result :failed}))

(defmethod make-script :install [_ package-or-packages]
  (let [package-string (if (string? package-or-packages)
                         package-or-packages
                         (string/join " " package-or-packages))]
    (str "DEBIAN_FRONTEND=noninteractive apt-get install -y " package-string)))

(defmethod process-result :install
  [_ _ {:keys [out err exit] :as result}]
  (if (zero? exit)
    (let [[_ upgraded installed removed]
          (re-find #"(\d+)\s*\w*\s*upgrade\w*, (\d+)\s*\w*\s*newly instal\w*, (\d+) to remove" out)

          upgraded (Integer/parseInt upgraded)
          installed (Integer/parseInt installed)
          removed (Integer/parseInt removed)
          ]
      (assoc result
             :result (if (and (zero? upgraded)
                              (zero? installed)
                              (zero? removed))
                       :ok
                       :changed)
             :out-lines (string/split out #"\n")
             :packages {:upgraded upgraded
                        :installed installed
                        :removed removed}))
    (assoc result :result :failed))
  #_(cond
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

(utils/defmodule apt [command opts]
  [host-string session]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts))))
