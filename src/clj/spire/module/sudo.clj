(ns spire.module.sudo
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]))

(defonce passwords (atom {}))

(defn make-sudo-command [{:keys [username group uid gid]} prompt command]
  (let [user-flags (cond
                     username (format "-u '%s'" username)
                     uid (format "-u '#%d'" uid)
                     :else "")
        group-flags (cond
                      group (format "-g '%s'" group)
                      gid (format "-g '#%d'" gid)
                      :else "")]
    (format "sudo -S -p '%s' %s %s %s" prompt user-flags group-flags command)))

(defn prefix-sudo-stdin [{:keys [password required?]} stdin]
  (if required?
    (str password "\n" stdin)
    stdin))

(defn requires-password?
  "tests the remote calling of sudo to determine if a password is
  required to change user, or if it is passwordless. The result of
  this alters the way subsequent calls to sudo are initiated."
  [opts]
  (let [session state/*connection*
        cmd (make-sudo-command opts "password required" "id")
        {:keys [out err exit]} (ssh/ssh-exec session cmd "" "UTF-8" {})]
    (cond
      (and (= 1 exit) (string/starts-with? err "password required") (= "" out))
      true

      (and (= 0 exit) (string/starts-with? out "uid=") (= "" err))
      false

      :else
      (assert
       false
       (format "Unknown response from sudo test in requires-password? exit: %d err: %s out: %s"
               exit (prn-str err) (prn-str out))))))

(defn sudo-id
  "actually escallates privileges using sudo and calls the system
  command `id`. This both tests if the password (if needed) is correct,
  and gathers user/group data for the escallated session that is then
  used to update system facts while in the body of the sudo macro."
  [opts]
  (let [session state/*connection*
        cmd (make-sudo-command opts "" "id")
        {:keys [err out exit]} (ssh/ssh-exec session cmd (prefix-sudo-stdin opts "") "UTF-8" {})]
    (cond
      (and (= 1 exit) (.contains err "incorrect password") (= "" out))
      (throw (ex-info "sudo: incorrect password" {:module :sudo :cause :incorrect-password}))

      (zero? exit)
      (facts/update-facts-user! (string/split-lines out))

      :else
      (assert
       false
       (format "Unknown response from sudo id in sudo-id exit: %d err: %s out: %s"
               exit (prn-str err) (prn-str out))))))

(defmacro sudo-user [conf & body]
  `(let [conf# ~conf
         required?# (requires-password? conf#)
         host-config# (state/get-host-config)
         store-key# (select-keys host-config# [:username :hostname :port])
         stored-password# (get @passwords store-key#)
         password# (get conf# :password stored-password#)
         full-conf# (assoc conf# :password password# :required? required?#)]

     ;; TODO: if password is required and not specified, prompt for it at the
     ;; terminal, and block thread here until its available.
     (assert
      (or (not required?#) (not (nil? password#)))
      "sudo password is required but not specified")

     (swap! passwords assoc store-key# password#)

     (let [original-facts# (facts/get-fact)]
       (sudo-id full-conf#)

       (binding [state/*shell-context*
                 {:exec :sudo
                  :shell-fn (partial make-sudo-command full-conf# "")
                  :stdin-fn (partial prefix-sudo-stdin full-conf#)}]
         (let [result# (do ~@body)]
           (facts/replace-facts-user! (:user original-facts#))
           result#)))))

(defmacro sudo [& body]
  `(sudo-user {} ~@body))
