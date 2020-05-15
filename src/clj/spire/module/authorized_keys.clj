(ns spire.module.authorized-keys
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.facts :as facts]
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
  (facts/check-bins-present #{:sed :cut :bash :id}))

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
  (facts/check-bins-present #{:sed :cut :bash :id})
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
  (facts/check-bins-present #{:bash}))

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

(utils/defmodule authorized-keys* [command {:keys [user key options file] :as opts}]
  [host-config session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (or
   (preflight command opts)
   (->>
    (exec-fn session (shell-fn "bash") (stdin-fn (make-script command opts)) "UTF-8" {})
    (process-result command opts))))

(defmacro authorized-keys
  "Add and remove ssh authorized keys to a users accounts.
  (authorized-keys command opts)

  `command`: The operation to execute. Should be one of `:present`,
  `:absent` or `:get`

  `opts`: A hashmap of options with the following keys:

  `:user` The username od the user to receive the credential in their
  authorized_keys file.

  `:key` The public key contents to add or remove from the file.

  `:file` Instead of specifying a user, specify the location of an
  authorized_keys file to work with.

  `:options` A hashmap containing a set of ssh key options that will
  be prepended to the key in the authorized_keys file.
  "
  [& args]
  `(utils/wrap-report ~&form (authorized-keys* ~@args)))

(def documentation
  {
   :module "authorized-keys"
   :blurb "add and removed ssh authorized keys to users accounts"
   :description
   [
    "This module manages users' ssh authorized key lists."
    "It can add a key to an authorized_keys file."
    "It can ensure a key is not present in an authorized_keys file."
    "It can return all the public keys in the authorized_keys file."]
   :form "(authorized-keys command options)"
   :args
   [{:arg "command"
     :desc "The operation to execute. Should be one of `:present`, `:absent` or `:get`"
     :values
     [[:present "Ensure the specified key is in the authorized_keys file"
       :absent "Ensure the specified key is removed from the authorized_keys file"
       :get "Return every key present in the authorized_keys file"]]}
    {:arg "options"
     :desc "A hashmap of options. All available options and their values are described below"}]

   :opts
   [
    [:user
     {:description ["The username of the user to receive the credential in their authorized_keys file"]
      :type :string
      :required false}]

    [:key
     {:description ["The public key contents to add or remove from the file"]
      :type :string
      :required false}]

    [:file
     {:description ["Instead of specifying a user, specify the location of an authorized_keys file to work with"]
      :type :string
      :required false}]

    [:options
     {:description ["A hashmap containing a set of ssh key options that will be prepended to the key in the authorized_keys file."]
      :type :string}]]})
