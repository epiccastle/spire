(ns spire.transfer
  (:require [spire.probe :as probe]
            [spire.utils :as utils]
            [spire.ssh-agent :as ssh-agent]
            [spire.ssh :as ssh]
            [spire.known-hosts :as known-hosts]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as string]
            [edamame.core :as edamame])
  (:import [com.jcraft.jsch JSch]))

(defn read-until-newline [inputstream]
  (loop [data []]
    (let [c (.read inputstream)]
      (if (= (int \newline) c)
        (str (apply str (map char data)))
        (recur (conj data c))))))

(defn ssh-line [host-string lines]
  (let [
        [username hostname] (ssh/split-host-string host-string)
        agent (JSch.)
        session (ssh/make-session
                 agent hostname
                 {:username username})
        irepo (ssh-agent/make-identity-repository)
        _ (when (System/getenv "SSH_AUTH_SOCK")
            (.setIdentityRepository session irepo))
        user-info (ssh/make-user-info)
        ]
    (doto session
      (.setHostKeyRepository (known-hosts/make-host-key-repository))
      (.setUserInfo user-info)
      (.connect))

    (let [runner (partial ssh/ssh-exec session)
          commands (probe/commands runner)
          local-spire (utils/which-spire)
          local-spire-digest (digest/md5 (io/as-file local-spire))
          spire-dest (str "/tmp/spire-" local-spire-digest)]
      (utils/push commands host-string runner session local-spire spire-dest)

      (let [result (runner (format "%s --server" spire-dest)
                           (pr-str line)
                           "" {})]
        (.disconnect session)
        (update result :out edamame/parse-string)))
    ))

(defmacro ssh [host-string & body]
  `(ssh-line ~host-string '(vector ~@body)))
