(ns spire.ssh
  (:require [clojure.string :as string])
  (:import [com.jcraft.jsch UserInfo]
           [com.jcraft.jsch
            JSch Session ChannelExec]
           [java.io
            PipedInputStream PipedOutputStream
            ByteArrayInputStream ByteArrayOutputStream
            ]))

(def debug false)

(defn print-flush-ask-yes-no [s]
  (print (str s " "))
  (.flush *out*)
  (let [response (read-line)
        first-char (first response)]
    (boolean (#{\y \Y} first-char))))

(defn make-user-info [{:keys [strict-host-key-checking
                              accept-host-key]}]
  (let [state (atom nil)]
    (proxy [UserInfo] []

      (getPassword []
        (print (str "Enter " @state ": "))
        (.flush *out*)
        (SpireUtils/enter-raw-mode 0)
        (let [password (read-line)]
          (SpireUtils/leave-raw-mode 0)
          (println)
          password))

      (promptYesNo [s]
        (let [host-key-missing? (and (.contains s "authenticity of host")
                                     (.contains s "can't be established"))]
          (if host-key-missing?
            (let [fingerprint (second (re-find #"fingerprint is ([0-9a-fA-F:]+)." s))]
              (cond
                (#{false "no" :no "n" :n} strict-host-key-checking)
                true

                (#{true "yes" :yes "y" :y "always" :always} accept-host-key)
                true

                (and (string? accept-host-key)
                     (= (string/lower-case fingerprint)
                        (string/lower-case accept-host-key)))
                true

                accept-host-key
                false

                :else
                (print-flush-ask-yes-no s)))
            (print-flush-ask-yes-no s))))

      (getPassphrase []
        (print (str "Enter " @state ": "))
        (.flush *out*)
        (SpireUtils/enter-raw-mode 0)
        (let [password (read-line)]
          (SpireUtils/leave-raw-mode 0)
          (println)
          password))

      (promptPassphrase [s]
        (reset! state s)
        ;; true decrypt key
        ;; false cancel key decrypt
        true
        )

      (promptPassword [s]
        (reset! state s)
        ;; return true to continue
        ;; false to cancel auth
        true
        )

      (showMessage [s]
        (println "showing...")
        (println s)))))

(defn- to-camel-case [^String a]
  (apply str (map string/capitalize (.split (name a) "-"))))

(defn- string-to-byte-array [^String s]
  (byte-array (map int s)))

(defn ^Session make-session
  "Start a SSH session.
Requires hostname.  You can also pass values for :username, :password and :port
keys.  All other option key pairs will be passed as SSH config options."
  [^JSch agent hostname
   {:keys [port username password identity passphrase private-key public-key] :or {port 22} :as options}]
  (let [username (or username (System/getProperty "user.name"))
        session-options (dissoc options :password :port :passphrase :identity :private-key :public-key)
        session (.getSession agent username hostname port)]
    (when password (.setPassword session password))
    ;; jsch.addIdentity(chooser.getSelectedFile().getAbsolutePath())
    (when identity
      (if passphrase
        (.addIdentity agent identity passphrase)
        (.addIdentity agent identity)))

    (when private-key
      (.addIdentity agent
                    (if (:username options)
                      (format "inline key for %s@%s" username hostname)
                      (format "inline key for %s" hostname)
                      )
                    (string-to-byte-array private-key)
                    (string-to-byte-array (or public-key ""))
                    (string-to-byte-array (or passphrase "")))
      )
    ;; :strict-host-key-checking
    (doseq [[k v] session-options]
      (.setConfig session (to-camel-case k) (if (boolean? v)
                                              (if v "yes" "no")
                                              (name v))))
    session))

(defn ssh-exec-proc
  "Run a command via exec, returning a map with the process streams."
  [^Session session ^String cmd
   {:keys [agent-forwarding pty in out err] :as opts}]
  (let [^ChannelExec exec (.openChannel session "exec")]
    (doto exec
      (.setCommand cmd)
      (.setInputStream in false))
    (when (contains? opts :pty)
      (.setPty exec (boolean (opts :pty))))
    (when (contains? opts :agent-forwarding)
      (.setAgentForwarding exec (boolean (opts :agent-forwarding))))

    (when out
      (.setOutputStream exec out))
    (when err
      (.setErrStream exec err))
    (let [resp {:channel exec
                :out (or out (.getInputStream exec))
                :err (or err (.getErrStream exec))
                :in (or in (.getOutputStream exec))}]
      (.connect exec)
      resp)))

(def ^java.nio.charset.Charset ascii
  (java.nio.charset.Charset/forName "US-ASCII"))

(def ^java.nio.charset.Charset utf-8
  (java.nio.charset.Charset/forName "UTF-8"))


(def
  ^{:dynamic true
    :doc "The buffer size (in bytes) for the piped stream used to implement
    the :stream option for :out. If your ssh commands generate a high volume of
    output, then this buffer size can become a bottleneck. You might also
    increase the frequency with which you read the output stream if this is an
    issue."}
  *piped-stream-buffer-size* (* 1024 10))

(defn- streams-for-out
  [out]
  (if (= :stream out)
    (let [os (PipedOutputStream.)]
      [os (PipedInputStream. os (int *piped-stream-buffer-size*))])
    [(ByteArrayOutputStream.) nil]))

(defn streams-for-in
  []
  (let [os (PipedInputStream. (int *piped-stream-buffer-size*))]
    [os (PipedOutputStream. os)]))

(defn string-stream
  "Return an input stream with content from the string s."
  [^String s]
  {:pre [(string? s)]}
  (ByteArrayInputStream. (.getBytes s utf-8)))

(defn ssh-exec
  "Run a command via ssh-exec.

  cmd        specifies a command string to exec.  If no cmd is given, a shell
             is started and input is taken from :in.
  in         specifies input to the remote shell. A string or a stream.
  out        specify :stream to obtain a an [inputstream shell]
             specify :bytes to obtain a byte array
             or specify a string with an encoding specification for a
             result string.  In the case of :stream, the shell can
             be polled for connected status.
  "
  [^Session session ^String cmd in out opts]
  (when debug (prn 'ssh-exec session cmd in out opts))
  (let [[^PipedOutputStream out-stream
         ^PipedInputStream out-inputstream] (streams-for-out out)
        [^PipedOutputStream err-stream
         ^PipedInputStream err-inputstream] (streams-for-out out)
        proc (ssh-exec-proc
              session cmd
              (merge
               {:in (if (string? in) (string-stream in) in)
                :out out-stream
                :err err-stream}
               opts))
        ^ChannelExec exec (:channel proc)]
    (let [res (if out-inputstream
                {:channel exec
                 :out-stream out-inputstream
                 :err-stream err-inputstream}
                (do (while (.isConnected exec)
                      (Thread/sleep 100))
                    {:exit (.getExitStatus exec)
                     :out (if (= :bytes out)
                            (.toByteArray ^ByteArrayOutputStream out-stream)
                            (.toString out-stream))
                     :err (if (= :bytes out)
                            (.toByteArray ^ByteArrayOutputStream err-stream)
                            (.toString err-stream))}))]
      (when debug (prn "result:" res))
      res)))

(defn split-host-string [host-string]
  (if (.contains ^String host-string "@")
    (string/split host-string #"@")
    [(System/getProperty "user.name") host-string]))

(def default-port 22)

(defn parse-host-string [host-string]
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

(defn host-description-to-host-config [host-description]
  (if-not (string? host-description)
    (do
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

    ;; todo: feels warty to recurse here. breakout key annotation and default filling to a seperate func
    (host-description-to-host-config
     (parse-host-string host-description))))
