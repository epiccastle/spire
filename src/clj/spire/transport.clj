(ns spire.transport
  (:require [spire.ssh :as ssh]
            [spire.ssh-agent :as ssh-agent]
            [spire.known-hosts :as known-hosts]
            [clojure.set :as set])
  (:import [com.jcraft.jsch JSch]))

;; all the open ssh connections
;; keys => [username hostname]
;; value => session
(defonce state
  (atom {}))

;; the currect execution sessions
(def ^:dynamic *sessions* [])

;;(defonce )

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
  `(try
     (connect ~host-string)
     (binding [*sessions* ~[host-string]]
       ~@body)
     (finally
       (disconnect ~host-string))))

(defmacro ssh-group [host-strings & body]
  `(try
     (doseq [host-string ~host-strings]
       (connect host-string))
     (binding [*sessions* ~host-strings]
       ~@body)
     (finally
       (doseq [host-string ~host-strings]
         (disconnect host-string)))))

(defmacro on [host-strings & body]
  `(let [present-sessions# (into #{} @*sessions*)
         sessions# (into #{} ~host-strings)
         subset# (into [] (clojure.set/intersection present-sessions# sessions#))]
     (binding [*sessions* subset#]
       ~@body)))

(defn sh [cmd in out & [opts]]
  (let [opts (or opts {})
        channel-futs
        (->> *sessions*
             (map
              (fn [host-string]
                (let [[username hostname] (ssh/split-host-string host-string)
                      session (get @state [username hostname])]
                  {:host-string host-string
                   :username username
                   :hostname hostname
                   :session session
                   :fut (future (ssh/ssh-exec session cmd in out opts))})))
             doall)]
    (->> channel-futs
         (map (fn [{:keys [host-string fut] :as exec}]
                [host-string @fut]))
         (into {}))))
