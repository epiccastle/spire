(ns spire.module.sudo
  (:require [spire.state :as state]
            [spire.context :as context]
            [spire.facts :as facts]
            [spire.sudo]
            [clojure.string :as string])
  )

(defonce passwords (atom {}))

(defn requires-password?
  "tests the remote calling of sudo to determine if a password is
  required to change user, or if it is passwordless. The result of
  this alters the way subsequent calls to sudo are initiated."
  [opts]
  (let [
        {:keys [exec-fn exec]} (state/get-shell-context)
        session (state/get-connection)
        cmd (spire.sudo/make-sudo-command opts "password required" "id")
        ;;_ (prn 'requires? cmd)
        {:keys [out err exit]}
        (if (= :local exec)
          (exec-fn nil cmd "" "UTF-8" {})
          (exec-fn session cmd "" "UTF-8" {}))]
    ;;(prn 'exit exit 'out out 'err err)
    (cond
      (and (= 1 exit) (string/starts-with? err "password required") (= "" out))
      true

      (and (= 0 exit) (string/starts-with? out "uid="))
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
  [sudo]
  (let [{:keys [exec-fn exec]} (state/get-shell-context)
        session (state/get-connection)
        cmd (spire.sudo/make-sudo-command sudo "" "id")

        #_ (prn 'SUDO 'sudo-id
                {:exec exec
                 :exec-fn exec-fn
                 :session session
                 :cmd cmd})

        in (if (:required? sudo)
             (spire.sudo/prefix-sudo-stdin sudo "")
             "")

        ;; cmd (if sudo
        ;;       (spire.sudo/make-sudo-command sudo "" cmd)
        ;;       cmd)

        ;;_ (prn 'sudo-id cmd)

        {:keys [err out exit]}
        (if (= :local exec)
          (exec-fn nil cmd in #_(prefix-sudo-stdin opts "") "UTF-8"
                   {} #_ {:sudo {:opts sudo-opts
                           :stdin? true
                           :shell? false}
                    })
          (exec-fn session cmd in "UTF-8"
                   {} #_ {:sudo {:opts sudo-opts
                           :stdin? true
                           :shell? false}}))]
    ;;(prn 'SUDO 'sudo-id {:exit exit :out out :err err})

    ;;(prn 'exit exit 'out out 'err err)

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

     ;;(prn 'sudo-user 'requires-password? required?#)

     ;; TODO: if password is required and not specified, prompt for it at the
     ;; terminal, and block thread here until its available.
     (assert
      (or (not required?#) (not (nil? password#)))
      "sudo password is required but not specified")

     (swap! passwords assoc store-key# password#)

     (let [original-facts# (facts/get-fact)]
       (sudo-id full-conf#)

       (context/binding* [state/shell-context
                          {:privilege :sudo
                           :sudo full-conf#
                           :exec (:exec (state/get-shell-context))
                           :exec-fn (:exec-fn (state/get-shell-context))
                           ;;:shell-fn (partial make-sudo-command full-conf# "")
                           ;;:stdin-fn (partial prefix-sudo-stdin full-conf#)
                           }]
                         (let [result# (do ~@body)]
                           (facts/replace-facts-user! (:user original-facts#))
                           result#)))))

(defmacro sudo [& body]
  `(spire.module.sudo/sudo-user {} ~@body))
