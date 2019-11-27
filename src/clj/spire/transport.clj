(ns spire.transport
  (:require [spire.ssh :as ssh]
            [spire.ssh-agent :as ssh-agent]
            [spire.known-hosts :as known-hosts])
  (:import [com.jcraft.jsch JSch]))

;; all the open ssh connections
;; keys => [username hostname]
;; value => session
(defonce state
  (atom {}))

;; the currect execution sessions
(def ^:dynamic *sessions* [])

(defn connect [host-string]
  (let [[username hostname] (ssh/split-host-string host-string)
        agent (JSch.)
        session (ssh/make-session agent hostname {:username username})
        irepo (ssh-agent/make-identity-repository)
        user-info (ssh/make-user-info)
        ]
    (when (System/getenv "SSH_AUTH_SOCK")
      (.setIdentityRepository session irepo))
    (doto session
      (.setHostKeyRepository (known-hosts/make-host-key-repository))
      (.setUserInfo user-info)
      (.connect))

    (swap! state assoc [username hostname] session)
    session))

(defn disconnect [host-string]
  (let [[username hostname] (ssh/split-host-string host-string)]
    (swap! state
           (fn [s]
             (-> s
                 (get [username hostname])
                 .disconnect)
             (dissoc s [username hostname])))))

(defmacro ssh [host-string & body]
  `(do
     (binding [*sessions* [(connect ~host-string)]]
       ~@body)
     (disconnect ~host-string)))

(defn sh [cmd in out & [opts]]
  (let [opts (or opts {})
        channel-futs (doall (map #(future (ssh/ssh-exec % cmd in out opts)) *sessions*))]
    (map deref channel-futs)))
