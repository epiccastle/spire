(ns spire.transport
  (:require [spire.state :as state]
            [spire.facts :as facts]
            [spire.local :as local]
            [clojure.string :as string]
            [clojure.stacktrace]
            [clojuressh.core :as clojuressh]
            [clojuressh.session :as session]))

(def debug false)

(defn host-config-to-connection-key [host-config]
  (select-keys host-config [:username :hostname :port])
  )

(defn connect [host-config]
  (when debug (prn 'connect host-config))
  (clojuressh/ssh (:hostname host-config) host-config))

(defn disconnect [client]
  (when debug (prn 'disconnect client))
  (session/disconnect client))

(defn disconnect-all! []
  (let [[connections _] (reset-vals! state/ssh-connections {})]
    (doall
     (for [[_ {:keys [connection]}] connections]
       (disconnect connection)))))

(defn open-connection [host-config]
  (when debug (prn 'open-connection host-config))
  (let [conn-key (host-config-to-connection-key host-config)
        new-state (swap! state/ssh-connections
                         update conn-key
                         (fn [{:keys [connection use-count]}]
                           (if (not connection)
                             {:connection (connect host-config)
                              :use-count 1}
                             {:connection connection
                              :use-count (inc use-count)})))
        new-conn (get-in new-state [conn-key :connection])]
    new-conn))

(defn close-connection [host-config]
  (when debug (prn 'close-connection host-config))
  (let [conn-key (host-config-to-connection-key host-config)]
    (swap! state/ssh-connections
           (fn [s]
             (let [{:keys [connection use-count] :as conn} (get s conn-key)]
               (when conn
                 (if (= 1 use-count)
                   (do
                     (disconnect connection)
                     (dissoc s conn-key))
                   (update-in s [conn-key :use-count] dec))))))
    nil))


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
         (binding [state/*host-config* host-config#
                   state/*connection* conn#
                   state/*shell-context* {:privileges :normal
                                          :exec :ssh
                                          :exec-fn ssh/ssh-exec
                                          }]
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
                   (binding [state/*host-config* host-config#
                             state/*connection* (get-connection
                                                  (ssh/host-config-to-connection-key
                                                   host-config#))
                             state/*shell-context* {:privileges :normal
                                                    :exec :ssh
                                                    :exec-fn ssh/ssh-exec
                                                    }]
                     (facts/update-facts!)
                     (let [result# (do ~@body)]
                       result#)))])))]
       (into {} (map (fn [[host-name# fut#]] [host-name# (safe-deref fut#)]) threads#)))
     (finally
       (doseq [host-string# ~host-strings]
         (let [host-config# (ssh/host-description-to-host-config host-string#)]
           (close-connection host-config#))))))

(defmacro local [& body]
  `(binding [state/*host-config* {:key "local"}
             state/*connection* nil
             state/*shell-context* {:privileges :normal
                                    :exec :local
                                    :exec-fn local/local-exec
                                    }]
     (do ~@body)))
