(ns spire.transport
  (:require [spire.state :as state]
            [spire.facts :as facts]
            [spire.local :as local]
            [spire.sudo :as sudo]
            [clojure.string :as string]
            [clojure.stacktrace]
            [clojuressh.core :as clojuressh]
            [clojuressh.session :as session])
  (:import [java.io
            PipedInputStream PipedOutputStream
            ByteArrayInputStream ByteArrayOutputStream
            ])
  )

(def debug false)

(def default-port 22)

(defn parse-host-string
  "parse a host-string and return a hashmap containing the elements.
  If no username is specified, then the field is not included"
  [host-string]
  (let [[_ username hostname port] (re-matches #"(.+)@(.+):(\d+)" host-string)]
    (if username
      {:username username
       :hostname hostname
       :port (Integer/parseInt port)}
      (let [[_ username hostname] (re-matches #"(.+)@(.+)" host-string)]
        (if username
          {:username username
           :hostname hostname
           :port default-port}
          (let [[_ hostname port] (re-matches #"(.+):(\d+)" host-string)]
            (if hostname
              {:hostname hostname
               :port (Integer/parseInt port)}
              {:hostname host-string
               :port default-port})))))))

(defn host-config-to-string [{:keys [hostname username port]}]
  (cond
    (and hostname username port (not= 22 port)) (format "%s@%s:%d" username hostname port)
    (and hostname port (not= 22 port)) (format "%s:%d" hostname port)
    (and username hostname) (format "%s@%s" username hostname)
    :else hostname))

(defn host-config-to-connection-key [host-config]
  (select-keys host-config [:username :hostname :port])
  )

(defn fill-in-host-description-defaults [host-description]
  (assert (not (and (:host-string host-description)
                    (:hostname host-description)))
          "cant have both host-string and hostname set in description.")
  (if (:host-string host-description)
    (let [{:keys [username hostname port] :as parsed} (parse-host-string (:host-string host-description))]
      (-> host-description
          (update :key #(or % (host-config-to-string parsed)))
          (assoc :username username ;; would be nil if none specified
                 :hostname hostname
                 :port port)))

    (-> host-description
        (update :key #(or % (host-config-to-string host-description)))
        (assoc :host-string (host-config-to-string host-description)))))

(defn host-description-to-host-config [host-description]
  (if-not (string? host-description)
    (fill-in-host-description-defaults host-description)
    (fill-in-host-description-defaults (parse-host-string host-description))))

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

(defn ssh-exec [session command in out {:keys [sudo agent-forwarding] :as opts}]
  #_(prn 'ssh-exec session command in out opts)
  (let [in (if (:required? sudo)
             (spire.sudo/prefix-sudo-stdin sudo in)
             in)

        command (if sudo
                  (spire.sudo/make-sudo-command sudo "" command)
                  command)

        common {:in in :agent-forwarding agent-forwarding}
        proc (clojuressh/exec session command
                              (cond
                                (= :stream out) (assoc common :out :stream :err :stream)
                                (= :bytes out) (assoc common :out :bytes :err :bytes)
                                :else (assoc common :out-enc out :out :string :err-enc out :err :string)))]
    (if (= :stream out)
      {:channel proc
       :out-stream (:out proc)
       :err-stream (:err proc)}
      (let [res @proc]
        #_(prn 'res res)
        {:exit (:exit res)
         :out (:out res)
         :err (:err res)}))))

(defmacro ssh [host-string & body]
  `(let [host-config# (host-description-to-host-config ~host-string)]
     (try
       (let [conn# (open-connection host-config#)]
         (binding [state/*host-config* host-config#
                   state/*connection* conn#
                   state/*shell-context* {:privileges :normal
                                          :exec :ssh
                                          :exec-fn ssh-exec}]
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
