(ns spire.transport
  (:require [spire.ssh :as ssh]
            [spire.output :as output]
            [spire.state :as state]
            [spire.ssh-agent :as ssh-agent]
            [spire.known-hosts :as known-hosts]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.stacktrace])
  (:import [com.jcraft.jsch JSch]))

(defn connect [host-config]
  (let [
        agent (JSch.)
        session (ssh/make-session agent (:hostname host-config) host-config)
        irepo (ssh-agent/make-identity-repository)
        user-info (ssh/make-user-info host-config)
        ]
    (when (System/getenv "SSH_AUTH_SOCK")
      (.setIdentityRepository session irepo))
    (doto session
      (.setHostKeyRepository (known-hosts/make-host-key-repository))
      (.setUserInfo user-info)
      (.connect))

    (swap! state/ssh-connections assoc
           (ssh/host-config-to-connection-key host-config) session)
    session))

(defn disconnect [host-config]
  (let [connection-key (ssh/host-config-to-connection-key host-config)]
    (swap! state/ssh-connections
           (fn [s]
             (some-> s
                     (get connection-key)
                     .disconnect)
             (dissoc s connection-key)))))

(defn flush-out []
  (.flush *out*))

(defn safe-deref [fut]
  (try
    (deref fut)
    (catch java.util.concurrent.ExecutionException e
      (let [cause (.getCause e)
            cause-data (some->> e .getCause ex-data)]
        (if cause-data
          cause-data
          {:result :failed
           :exception e
           :traceback (string/split-lines (with-out-str (clojure.stacktrace/print-stack-trace e)))
           ;;:exc-data (ex-data e)
           :cause (.getCause e)
           :cause-traceback (when (.getCause e)
                              (string/split-lines (with-out-str (clojure.stacktrace/print-stack-trace (.getCause e)))))
           :cause-data (some->> e .getCause ex-data)
           })))))


(defmacro ssh [host-string & body]
  `(let [host-config# (ssh/host-description-to-host-config ~host-string)]
     (try
       (let [conn# (connect host-config#)]
         (binding [ ;;state/*sessions* ~[host-string]
                   ;;state/*connections* ~[host-string]
                   state/*host-config* host-config#

                   #_state/*host-string*
                   #_(ssh/host-config-to-string
                      (ssh/host-description-to-host-config ~host-string))
                   state/*connection* conn#
                   ]
           (safe-deref (future ~@body))))
       (finally
         (disconnect host-config#)))))


(defmacro ssh-group [host-strings & body]
  `(try
     (doseq [host-string# ~host-strings]
       (let [host-config# (ssh/host-description-to-host-config host-string#)]
         (connect host-config#)))
     (let [threads#
           (doall
            (for [host-string# ~host-strings]
              (let [host-config# (ssh/host-description-to-host-config host-string#)]
                [(:key host-config#)
                 (future
                   (binding [state/*host-config* host-config#
                             state/*connection* (get @state/ssh-connections
                                                     (ssh/host-config-to-connection-key
                                                      host-config#))]
                     (let [result# (do ~@body)]
                       result#)))])))]
       (into {} (map (fn [[host-name# fut#]] [host-name# (safe-deref fut#)]) threads#)))
     (finally
       (doseq [host-string# ~host-strings]
         (let [host-config# (ssh/host-description-to-host-config host-string#)]
           (disconnect host-config#))))))

#_ (defmacro on [host-strings & body]
  `(let [present-sessions# (into #{} (state/get-sessions))
         sessions# (into #{} ~host-strings)
         subset# (into [] (clojure.set/intersection present-sessions# sessions#))
         futs# (->> subset#
                    (map
                     (fn [host-string#]
                       [host-string#
                        (binding [state/*host-string* host-string#
                                  state/*sessions* [host-string#]
                                  state/*connection* (get @state/ssh-connections host-string#)]
                          (future
                            ~@body))]))
                    (into {}))]
     (->> futs#
          (map (fn [[host-string# fut#]]
                 [host-string# @fut#]))
          (into {}))))
