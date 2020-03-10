(ns spire.module.sudo
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]))

(defn requires-password? [{:keys [username group uid gid]}]
  (let [session state/*connection*
        user-flags (cond
                     username (format "-u '%s'" username)
                     uid (format "-u '#%d'" uid)
                     :else "")
        group-flags (cond
                     group (format "-g '%s'" group)
                     gid (format "-g '#%d'" gid)
                     :else "")
        cmd (format "sudo -S -p 'password required' %s %s id" user-flags group-flags)
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

(defmacro sudo [conf & body]
  `(do
     ;; test if password is needed
     (requires-password? ~conf)

     ~@body))
