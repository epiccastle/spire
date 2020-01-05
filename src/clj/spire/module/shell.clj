(ns spire.module.shell
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.utils :as utils]
            [clojure.string :as string]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defn preflight [opts]
  nil)

(defn process-result [opts result]
  {:result :ok})

(defn make-env-string [env]
  (string/join
   " "
   (for [[k v] env] (format "%s=\"%s\"" (name k) (str v)))))

(utils/defmodule shell [{:keys [env dir shell out opts cmd] :as opts}]
  [host-string session]
  (or (preflight opts)
      (let [{:keys [exit out err] :as result}
            (ssh/ssh-exec
             session (or shell "bash")
             (format "cd \"%s\"; %s %s" dir (make-env-string env) cmd)
             (or out "UTF-8") (or opts {}))]
        (assoc result
               :out-lines (string/split-lines out)

               :result
               (cond
                 (zero? exit) :changed
                 :else :failed)))))
