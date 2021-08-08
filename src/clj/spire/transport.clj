(ns spire.transport
  (:require [spire.ssh :as ssh]
            [spire.state :as state]
            [spire.ssh-agent :as ssh-agent]
            [spire.facts :as facts]
            [spire.local :as local]
            [spire.known-hosts :as known-hosts]
            [spire.context :as context]
            [clojure.string :as string]
            [clojure.stacktrace])
  (:import [com.jcraft.jsch JSch]))

(def debug false)

(defn connect [host-config]
  (when debug (prn 'connect host-config))
  (let [
        agent (JSch.)
        session (ssh/make-session agent (:hostname host-config) host-config)
        irepo (ssh-agent/make-identity-repository)
        user-info (ssh/make-user-info host-config)
        ]
    (when (and (not (:identity host-config))
               (not (:private-key host-config))
               (System/getenv "SSH_AUTH_SOCK"))
      (.setIdentityRepository session irepo))
    (when debug (prn 'connect 'connecting))
    (doto session
      (.setHostKeyRepository (known-hosts/make-host-key-repository))
      (.setUserInfo user-info)
      (.connect))
    (when debug (prn 'connect 'connected))
    session
    ))

(defn disconnect [connection]
  (when debug (prn 'disconnect connection))
  (.disconnect connection))

(defn disconnect-all! []
  (let [[connections _] (reset-vals! state/ssh-connections {})]
    (doall
     (for [[_ {:keys [connection]}] connections]
       (disconnect connection)))))

(defn open-connection [host-config]
  (when debug (prn 'open-connection host-config))
  (let [conn-key (ssh/host-config-to-connection-key host-config)
        new-state (swap! state/ssh-connections
                         update conn-key
                         (fn [{:keys [connection use-count]}]
                           (if (not connection)
                             {:connection (connect host-config)
                              :use-count 1}
                             {:connection connection
                              :use-count (inc use-count)})))
        new-conn (get-in new-state [conn-key :connection])]
    (when debug
      (prn 'open-connection 'conn-key conn-key)
      (prn 'open-connection 'new-state new-state)
      (prn 'open-connection 'new-conn new-conn))
    new-conn))

(defn close-connection [host-config]
  (when debug (prn 'close-connection host-config))
  (let [conn-key (ssh/host-config-to-connection-key host-config)]
    (swap! state/ssh-connections
           (fn [s]
             (let [{:keys [connection use-count] :as conn} (get s conn-key)]
               (when conn
                 (if (= 1 use-count)
                   (do
                     (disconnect connection)
                     (dissoc s conn-key))
                   (update-in s [conn-key :use-count] dec))))))))

(defn get-connection [conn-key]
  (get-in @state/ssh-connections [conn-key :connection]))

(defn flush-out []
  (.flush *out*))

(defn safe-deref [fut]
  (try
    (deref fut)
    (catch java.util.concurrent.ExecutionException e
      (let [cause (.getCause e)
            cause-data (some->> cause ex-data)]
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
       (let [conn# (open-connection host-config#)]
         (context/binding* [state/host-config host-config#
                            state/connection conn#
                            state/shell-context {:privileges :normal
                                                 :exec :ssh
                                                 :exec-fn ssh/ssh-exec
                                                 :shell-fn identity
                                                 :stdin-fn identity}]
                           (facts/update-facts!)
                           (do ~@body)))
       (finally
         (close-connection host-config#)))))

(defmacro ssh-group [host-strings & body]
  `(try
     (doseq [host-string# ~host-strings]
       (let [host-config# (ssh/host-description-to-host-config host-string#)]
         (open-connection host-config#)))
     (let [threads#
           (doall
            (for [host-string# ~host-strings]
              (let [host-config# (ssh/host-description-to-host-config host-string#)]
                [(:key host-config#)
                 (future
                   (context/binding* [state/host-config host-config#
                                      state/connection (get-connection
                                                        (ssh/host-config-to-connection-key
                                                         host-config#))
                                      state/shell-context {:privileges :normal
                                                           :exec :ssh
                                                           :exec-fn ssh/ssh-exec
                                                           :shell-fn identity
                                                           :stdin-fn identity}]
                                     (facts/update-facts!)
                                     (let [result# (do ~@body)]
                                       result#)))])))]
       (into {} (map (fn [[host-name# fut#]] [host-name# (safe-deref fut#)]) threads#)))
     (finally
       (doseq [host-string# ~host-strings]
         (let [host-config# (ssh/host-description-to-host-config host-string#)]
           (close-connection host-config#))))))

#_(clojure.lang.RT/loadLibrary "spire")
#_(ssh "localhost" (spire.facts/get-fact))

(defmacro local [& body]
  `(context/binding* [state/host-config {:key "local"}
                      state/connection nil
                      state/shell-context {:privileges :normal
                                           :exec :local
                                           :exec-fn local/local-exec
                                           :shell-fn identity
                                           :stdin-fn identity}]
                     (do ~@body)))
