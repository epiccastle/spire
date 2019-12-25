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

(defn process-options [options]
  (string/join
   ","
   (for [[k v] options]
     (if (boolean? v)
       (name k)
       (str (name k) "=" (utils/double-quote (utils/path-escape v)))))))

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defmethod preflight :present [_ {:keys [user key options file] :as opts}]
  nil
  )

(defmethod make-script :present [_ {:keys [user key options file] :as opts}]
  (utils/make-script
   "authorized_keys_present.sh"
   {:USER user
    :KEY (string/trim key)
    :AUTHORIZED_KEYS_FILE file
    :OPTIONS (utils/string-escape (process-options options))}))

(defmethod process-result :present
  [_ {:keys [user key options file] :as opts} {:keys [out err exit] :as result}]
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

(defmethod preflight :absent [_ {:keys [user key options file] :as opts}]
  nil
  )

(defmethod make-script :absent [_ {:keys [user key file] :as opts}]
  (utils/make-script
   "authorized_keys_absent.sh"
   {:USER user
    :KEY (string/trim key)
    :AUTHORIZED_KEYS_FILE file}))

(defmethod process-result :absent
  [_ {:keys [user key file] :as opts} {:keys [out err exit] :as result}]
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

(defmethod preflight :get [_ {:keys [user file] :as opts}]
  nil
  )

(defmethod make-script :get [_ {:keys [user file] :as opts}]
  (utils/make-script
   "authorized_keys_get.sh"
   {:USER user
    :AUTHORIZED_KEYS_FILE file}))

(defmethod process-result :get
  [_ {:keys [user file] :as opts} {:keys [out err exit] :as result}]
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

(utils/defmodule authorized-keys [command {:keys [user key options file] :as opts}]
  [host-string session]
  (or
   (preflight command opts)
   (->>
    (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
    (process-result command opts))))
