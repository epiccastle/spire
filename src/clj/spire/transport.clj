(ns spire.transport
  (:require [spire.ssh :as ssh]
            [spire.output :as output]
            [spire.state :as state]
            [spire.ssh-agent :as ssh-agent]
            [spire.known-hosts :as known-hosts]
            [clojure.set :as set])
  (:import [com.jcraft.jsch JSch]))


(defn connect [host-string]
  (let [
        {:keys [username hostname port]} (ssh/parse-host-string host-string)

        agent-forward (if-not (string? host-string) (:agent-forward host-string) false)
        key-contents (if-not (string? host-string) (:key host-string) nil)
        key-file (if-not (string? host-string) (:key-file host-string) nil)
        key-passphrase  (if-not (string? host-string) (:key-passphrase host-string) nil)
        password (if-not (string? host-string) (:password host-string) nil)
        strict-host-key-checking (if-not (string? host-string) (:strict-host-key-checking host-string) :ask)

        agent (JSch.)
        session (ssh/make-session agent hostname {:username username
                                                  :password password
                                                  :port 22
                                                  })
        irepo (ssh-agent/make-identity-repository)
        user-info (ssh/make-user-info)
        ]
    (when (System/getenv "SSH_AUTH_SOCK")
      (.setIdentityRepository session irepo))
    (doto session
      (.setHostKeyRepository (known-hosts/make-host-key-repository))
      (.setUserInfo user-info)
      (.connect))

    (swap! state/ssh-connections assoc host-string session)
    session))

(defn disconnect [host-string]
  (swap! state/ssh-connections
         (fn [s]
           (some-> s
                   (get host-string)
                   .disconnect)
           (dissoc s host-string))))

(defmacro ssh [host-string & body]
  `(try
     (connect ~host-string)
     (binding [state/*sessions* ~[host-string]
               state/*connections* ~[host-string]
               state/*host-string* ~host-string
               state/*connection* (get @state/ssh-connections ~host-string)
               ]
       ~@body)
     (finally
       (disconnect ~host-string))))

(defn flush-out []
  (.flush *out*))

(defn safe-deref [[host-string fut]]
  (try
    [host-string (deref fut)]
    (catch java.util.concurrent.ExecutionException e
      (let [cause (.getCause e)
            cause-data (some->> e .getCause ex-data)]
        [host-string
         (if cause-data
           cause-data
           {:result :failed
            :exception e

            ;;:exc-data (ex-data e)
            ;; :cause (.getCause e)
            ;;:cause-data (some->> e .getCause ex-data)
            })]))))

(defmacro ssh-group [host-strings & body]
  `(try
     (doseq [host-string ~host-strings]
       (connect host-string))
     (binding [state/*sessions* ~host-strings
               state/*connections* ~host-strings]
       (let [threads#
             (for [host-string# ~host-strings]
               [host-string# (future
                               (binding [state/*host-string* host-string#
                                         state/*connection* (get @state/ssh-connections host-string#)]
                                 (let [result# (do ~@body)]
                                   result#)))])]
         (into {} (map safe-deref threads#))))
     (finally
       (doseq [host-string ~host-strings]
         (disconnect host-string)))))

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

#_ (defn psh [cmd in out & [opts]]
  (let [opts (or opts {})
        channel-futs
        (->> state/*sessions*
             (map
              (fn [host-string]
                (let [session (get @state/ssh-connections host-string)]
                  [host-string
                   {:session session
                    :fut (future (ssh/ssh-exec session cmd in out opts))}])))
             (into {}))]
    channel-futs))

#_ (defn pipelines [func]
  (let [channel-futs
        (->> state/*sessions*
             (map
              (fn [host-string]
                (let [session (get @state/ssh-connections host-string)]
                  [host-string
                   {:session session
                    :fut (future
                           (let [{:keys [result] :as data}
                                 (func host-string session)]
                             (output/print-result result host-string)
                             data))}])))
             (into {}))]
    (->> channel-futs
         (map (fn [[host-string {:keys [fut]}]]
                [host-string @fut]))
         (into {}))))
