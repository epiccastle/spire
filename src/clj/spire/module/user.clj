(ns spire.module.user
  (:require [spire.utils :as utils]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [clojure.string :as string])
  (:import [org.apache.commons.codec.digest Crypt]
           [java.security SecureRandom]))

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defmethod preflight :present [_ {:keys [user] :as opts}]
  (facts/check-bins-present [:grep :true :useradd :usermod :bash :chown :cp :cut :awk]))

(defmethod make-script :present [_ {:keys [name comment uid home
                                           group groups password shell
                                           create-home move-home
                                           ]
                                    :or {create-home true}
                                    :as opts}]
  (utils/make-script
    "user_present.sh"
    {:NAME name
     :COMMENT comment
     :USER_ID uid
     :HOME_DIR home
     :GROUP group
     :GROUPSET (some->> groups (string/join ","))
     :PASSWORD (some->> password utils/var-escape)
     :SHELL shell
     :CREATE_HOME (when create-home "true")
     :MOVE_HOME (when move-home "true")
     }))

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

(defmethod preflight :absent [_ {:keys [name] :as opts}]
  (facts/check-bins-present [:grep :true :userdel]))

(defmethod make-script :absent [_ {:keys [name] :as opts}]
  (utils/make-script
   "user_absent.sh"
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

(utils/defmodule user* [command {:keys [name] :as opts}]
  [host-string session {:keys [exec-fn sudo] :as shell-context}]
  (or
   (preflight command opts)
   (let [result (->>
                 (exec-fn session "bash" (make-script command opts) "UTF-8" {:sudo sudo})
                 (process-result command opts))]
     result)))

(defmacro user [& args]
  `(utils/wrap-report ~&form (user* ~@args)))

(defn gecos [{:keys [fullname room office home info]}]
  (str fullname "," room "," office "," home "," info))

(def b64t-chars "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz")

(defn make-salt [size]
  (let [rng (SecureRandom.)]
    (->>
     (repeatedly
      #(.charAt b64t-chars (.nextInt rng (count b64t-chars))))
     (take size)
     (apply str))))

(def salt-sizes
  {:sha256 16
   :sha512 16
   :md5 8
   :des 2})

(def salt-prefix
  {:sha256 "$5$"
   :sha512 "$6$"
   :md5 "$1$"
   :des ""})

(defn password [password & [{:keys [type salt]
                              :or {type :sha512}}]]
  (let [salt (or salt (make-salt (salt-sizes type)))
        prefix (salt-prefix type)]
    (Crypt/crypt ^String password ^String (str prefix salt))))

#_ (password "yourpass")

(def documentation
  {:module "user"
   :blurb "Manage the system users"
   :description
   ["This module ensures a particular user is present on, or absent from, the remote machine."
    "It ensures that the configuration of the user matches the values specified."
    "The user can be specified by username or by user id. If both are specified the user is found by name, and the id is set to match the id value."
    "Note: User creation and deletion honours the USERGROUPS_ENAB setting in /etc/login.defs on systems that use this."
    ]
   :form "(user command options)"
   :args
   [{:arg "command"
     :desc "The state the user should be in. Should be one of `:present` or `:absent`."
     :values
     [[:present "Ensures the named user is present and has the specified settings"]
      [:absent "Ensures the named user is absent"]]}
    {:arg "options"
     :desc "A hashmap of options. All available option keys and their values are described below"}]

   :opts
   [[:name
     {:description ["The username"]
      :type :string
      :required false}]
    [:uid
     {:description ["The user's id number"]
      :type :string
      :required false}]
    [:home
     {:description ["The user's home directory path"]
      :type :string
      :required false}]
    [:create-home
     {:description ["If the user's home directory does not exist, create it."]
      :type :boolean
      :default true
      :required false}]
    [:move-home
     {:description ["Move the user's home directory to the new location. Only effective when modifying an existing user."]
      :type :boolean
      :default false
      :required false}]
    [:group
     {:description ["The user's primary group."]
      :type :string
      :required false}]
    [:groups
     {:description ["A list of groups the user should belong to"]
      :type :vector
      :required false}]
    [:password
     {:description ["An encrypted password string to use for the user's password login."]
      :type :string
      :required false}]
    [:shell
     {:description ["The path to the users default shell."]
      :type :string
      :required false}]
    [:comment
     {:description ["The comment field to add against the user in the users database."]
      :type :string
      :required false}]]})

#_
[name comment uid home
 group groups password shell
 ]
