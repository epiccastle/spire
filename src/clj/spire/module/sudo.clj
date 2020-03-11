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

(defn requires-password? [{:keys [username group uid gid] :as opts}]
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

(defn sudo-id [{:keys [username group uid gid password] :as opts} required?]
  (let [session state/*connection*
        cmd (make-sudo-command opts "" "id")
        {:keys [err out exit]} (ssh/ssh-exec
                                session cmd
                                (if required?
                                  (str password "\n")
                                  "")
                                "UTF-8" {})]
    (cond
      (and (= 1 exit) (.contains err "incorrect password") (= "" out))
      (throw (ex-info "sudo: incorrect password" {:module :sudo :cause :incorrect-password}))

      (zero? exit)
      (facts/update-facts-user! (string/split-lines out))

      :else
      (assert
       false
       (format "Unknown response from sudo id in sudo-id exit: %d err: %s out: %s"
               exit (prn-str err) (prn-str out))
       )
      )
    )
  )

(defmacro sudo [conf & body]
  `(let [conf# ~conf
         required?# (requires-password? conf#)
         host-config# (state/get-host-config)
         store-key# (select-keys host-config# [:username :hostname :port])
         stored-password# (get @passwords store-key#)
         password# (get conf# :password stored-password#)]
     (assert
      (or (not required?#) (not (nil? password#)))
      "sudo password is required but not specified")
     (swap! passwords assoc store-key# password#)

     (let [original-facts# (facts/get-fact)]
       (sudo-id (assoc conf# :password password#) required?#)
       )
#_     [conf# password# required?# host-config#]

     ~@body))
