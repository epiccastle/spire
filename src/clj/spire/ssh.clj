(ns spire.ssh
  (:require [clojure.string :as string]
            [spire.sudo :as sudo])
  (:import [com.jcraft.jsch UserInfo]
           [com.jcraft.jsch
            JSch Session ChannelExec]
           [java.io
            PipedInputStream PipedOutputStream
            ByteArrayInputStream ByteArrayOutputStream
            ]))

(def debug false)

(def ctrl-c 3)
(def carridge-return 10)

(defn raw-mode-read-line
  "Read from stdin with terminal in raw mode. Detect ctrl-c break
  input and exit if found. Ends input on detection of carridge return.

  returns: the entered string or nil if ctrl-c pressed
  "
  []
  (SpireUtils/enter-raw-mode 0)
  (let [result (loop [text ""]
                 (let [c (.read *in*)]
                   (condp = c
                     ctrl-c nil
                     carridge-return text
                     (recur (str text (char c))))))]
    (SpireUtils/leave-raw-mode 0)
    result))

(defn print-flush-ask-yes-no [s]
  (print (str s " "))
  (.flush *out*)
  (let [response (read-line)
        first-char (first response)]
    (boolean (#{\y \Y} first-char))))

(defn make-user-info [{:keys [strict-host-key-checking
                              accept-host-key]}]
  (when debug (prn 'make-user-info
                   'strict-host-key-checking strict-host-key-checking
                   'accept-host-key accept-host-key
                   ))
  (let [state (atom nil)]
    (proxy [UserInfo] []

      (getPassword []
        (when debug (prn 'make-user-info 'getPassword))
        (print (str "Enter " @state ": "))
        (.flush *out*)
        (if-let [password (raw-mode-read-line)]
          (do
            (println)
            password)
          (do
            (println "^C")
            (System/exit 1))))

      (promptYesNo [s]
        (when debug (prn 'make-user-info 'promptYesNo s))
        (let [host-key-missing? (and (.contains s "authenticity of host")
                                     (.contains s "can't be established"))
              host-key-changed? (.contains s "IDENTIFICATION HAS CHANGED")]
          (when debug
            (prn 'make-user-info 'promptYesNo 'host-key-missing? host-key-missing?)
            (prn 'make-user-info 'promptYesNo 'host-key-changed? host-key-changed?))
          (cond
            host-key-missing?
            (let [fingerprint (second (re-find #"fingerprint is ([0-9a-fA-F:]+)." s))]
              (when debug (prn 'make-user-info 'promptYesNo 'fingerprint fingerprint))
              (cond
                (not (nil? (#{false "no" :no "n" :n} strict-host-key-checking)))
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

            ;; strict-host-key-checking=true: showMessage will be called with a refusal to connect
            ;; strict-host-key-checking=false: connection will proceed, but key will not be added
            ;; strict-host-key-checking=nil: question will be "Do you want to delete the old key and insert the new key?"
            host-key-changed?
            (let [fingerprint (second (re-find #"\n([0-9a-fA-F:]+)." s))]
              (when debug (prn 'make-user-info 'promptYesNo 'fingerprint fingerprint))
              (cond
                (#{true "yes" :yes "y" :y "always" :always} accept-host-key)
                true

                (not (nil? (#{false "no" :no "n" :n "never" :never} accept-host-key)))
                false

                (and (string? accept-host-key)
                     (= (string/lower-case fingerprint)
                        (string/lower-case accept-host-key)))
                true

                (string? accept-host-key)
                false

                :default
                (print-flush-ask-yes-no s)))

            :default
            (print-flush-ask-yes-no s)
            )))

      (getPassphrase []
        (when debug (prn 'make-user-info 'getPassphrase))
        (print (str "Enter " @state ": "))
        (.flush *out*)
        (if-let [password (raw-mode-read-line)]
          (do
            (println)
            password)
          (do
            (println "^C")
            (System/exit 1))))

      (promptPassphrase [s]
        (when debug (prn 'make-user-info 'promptPassphrase))
        (reset! state s)
        ;; true decrypt key
        ;; false cancel key decrypt
        true
        )

      (promptPassword [s]
        (when debug (prn 'make-user-info 'promptPassword))
        (reset! state s)
        ;; return true to continue
        ;; false to cancel auth
        true
        )

      (showMessage [s]
        (when debug (prn 'make-user-info 'showMessage))
        (println s)))))

#_ (make-user-info {})

(defn- to-camel-case [^String a]
  (apply str (map string/capitalize (.split (name a) "-"))))

(defn- string-to-byte-array [^String s]
  (byte-array (map int s)))

(defn ^Session make-session
  "Start a SSH session.
Requires hostname.  You can also pass values for :username, :password and :port
keys.  All other option key pairs will be passed as SSH config options."
  [^JSch agent hostname
   {:keys [port username password identity passphrase private-key public-key jsch-options]
    :or {port 22
         jsch-options {}}
    :as options}]
  (when debug (prn 'make-session agent hostname options))
  (let [username (or username (System/getProperty "user.name"))
        session-options (select-keys options [:agent-forwarding :strict-host-key-checking :accept-host-key :jsch-options])
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
      (when debug (prn 'make-session 'adding-option k v))
      (when-not (nil? v)
        (.setConfig session (to-camel-case k) (case v
                                                true "yes"
                                                false "no"
                                                (name v)))))

    ;; jsch-options
    (doseq [[k v] jsch-options]
      (let [fs
            {:kex #(.setConfig %1 "kex" %2)
             :server-host-key #(.setConfig %1 "server_host_key" %2)
             :prefer-known-host-key-types #(.setConfig %1 "server_host_key" %2)
             :enable-server-sig-algs #(.setConfig %1 "enable_server_sig_algs" %2)
             :cipher #(do
                        (.setConfig %1 "cipher.s2c" %2)
                        (.setConfig %1 "cipher.c2s" %2))
             :cipher-s2c #(.setConfig %1 "cipher.s2c" %2)
             :cipher-c2s #(.setConfig %1 "cipher.c2s" %2)
             :mac #(do
                     (.setConfig %1 "mac.s2c" %2)
                     (.setConfig %1 "mac.c2s" %2))
             :mac-s2c #(.setConfig %1 "mac.s2c" %2)
             :mac-c2s #(.setConfig %1 "mac.c2s" %2)
             :compression #(do
                             (.setConfig %1 "compression.s2c" %2)
                             (.setConfig %1 "compression.c2s" %2))
             :compression-s2c #(.setConfig %1 "compression.s2c" %2)
             :compression-c2s #(.setConfig %1 "compression.c2s" %2)
             :lang #(do
                      (.setConfig %1 "lang.s2c" %2)
                      (.setConfig %1 "lang.c2s" %2))
             :lang-s2c #(.setConfig %1 "lang.s2c" %2)
             :lang-c2s #(.setConfig %1 "lang.c2s" %2)
             :dhgex-min #(.setConfig %1 "dhgex_min" %2)
             :dhgex-max #(.setConfig %1 "dhgex_max" %2)
             :dhgex-preferred #(.setConfig %1 "dhgex_preferred" %2)
             :compression-level #(.setConfig %1 "compression_level" %2)
             :preferred-authentications #(.setConfig %1 "PreferredAuthentications" %2)
             :client-pubkey #(.setConfig %1 "PubkeyAcceptedAlgorithms" %2)
             :check-ciphers #(.setConfig %1 "CheckCiphers" %2)
             :check-macs #(.setConfig %1 "CheckMacs" %2)
             :check-kexes #(.setConfig %1 "CheckKexes" %2)
             :check-signatures #(.setConfig %1 "CheckSignatures" %2)
             :fingerprint-hash #(.setConfig %1 "FingerprintHash" %2)
             :max-auth-tries #(.setConfig %1 "MaxAuthTries" %2)

             :kex-fn #(.setConfig %1 "kex" (%2 (.getConfig "kex")))
             :server-host-key-fn #(.setConfig %1 "server_host_key" (%2 (.getConfig "server_host_key")))
             :prefer-known-host-key-types-fn #(.setConfig %1 "server_host_key" (%2 (.getConfig "server_host_key")))
             :enable-server-sig-algs-fn #(.setConfig %1 "enable_server_sig_algs" (%2 (.getConfig "enable_server_sig_algs")))
             :cipher-fn #(do
                           (.setConfig %1 "cipher.s2c" (%2 (.getConfig "cipher.s2c")))
                           (.setConfig %1 "cipher.c2s" (%2 (.getConfig "cipher.c2s"))))
             :cipher-s2c-fn #(.setConfig %1 "cipher.s2c" (%2 (.getConfig "cipher.s2c")))
             :cipher-c2s-fn #(.setConfig %1 "cipher.c2s" (%2 (.getConfig "cipher.c2s")))
             :mac-fn #(do
                        (.setConfig %1 "mac.s2c" (%2 (.getConfig "mac.s2c")))
                        (.setConfig %1 "mac.c2s" (%2 (.getConfig "mac.c2s"))))
             :mac-s2c-fn #(.setConfig %1 "mac.s2c" (%2 (.getConfig "mac.s2c")))
             :mac-c2s-fn #(.setConfig %1 "mac.c2s" (%2 (.getConfig "mac.c2s")))
             :compression-fn #(do
                                (.setConfig %1 "compression.s2c" (%2 (.getConfig "compression.s2c")))
                                (.setConfig %1 "compression.c2s" (%2 (.getConfig "compression.c2s"))))
             :compression-s2c-fn #(.setConfig %1 "compression.s2c" (%2 (.getConfig "compression.s2c")))
             :compression-c2s-fn #(.setConfig %1 "compression.c2s" (%2 (.getConfig "compression.c2s")))
             :lang-fn #(do
                         (.setConfig %1 "lang.s2c" (%2 (.getConfig "lang.s2c")))
                         (.setConfig %1 "lang.c2s" (%2 (.getConfig "lang.c2s"))))
             :lang-s2c-fn #(.setConfig %1 "lang.s2c" (%2 (.getConfig "lang.s2c")))
             :lang-c2s-fn #(.setConfig %1 "lang.c2s" (%2 (.getConfig "lang.c2s")))
             :dhgex-min-fn #(.setConfig %1 "dhgex_min" (%2 (.getConfig "dhgex_min")))
             :dhgex-max-fn #(.setConfig %1 "dhgex_max" (%2 (.getConfig "dhgex_max")))
             :dhgex-preferred-fn #(.setConfig %1 "dhgex_preferred" (%2 (.getConfig "dhgex_preferred")))
             :compression-level-fn #(.setConfig %1 "compression_level" (%2 (.getConfig "compression_level")))
             :preferred-authentications-fn #(.setConfig %1 "PreferredAuthentications" (%2 (.getConfig "PreferredAuthentications")))
             :client-pubkey-fn #(.setConfig %1 "PubkeyAcceptedAlgorithms" (%2 (.getConfig "PubkeyAcceptedAlgorithms")))
             :check-ciphers-fn #(.setConfig %1 "CheckCiphers" (%2 (.getConfig "CheckCiphers")))
             :check-macs-fn #(.setConfig %1 "CheckMacs" (%2 (.getConfig "CheckMacs")))
             :check-kexes-fn #(.setConfig %1 "CheckKexes" (%2 (.getConfig "CheckKexes")))
             :check-signatures-fn #(.setConfig %1 "CheckSignatures" (%2 (.getConfig "CheckSignatures")))
             :fingerprint-hash-fn #(.setConfig %1 "FingerprintHash" (%2 (.getConfig "FingerprintHash")))
             :max-auth-tries-fn #(.setConfig %1 "MaxAuthTries" (%2 (.getConfig "MaxAuthTries")))}
            f (fs k)]
        (when (and f (not (nil? v)))
          (f v))))

    (when debug (prn 'make-session 'returning session))

    session))

(defn ssh-exec-proc
  "Run a command via exec, returning a map with the process streams."
  [^Session session ^String cmd
   {:keys [agent-forwarding pty in out err] :as opts}]
  (when debug (prn 'ssh-exec-proc session cmd opts))
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
  *piped-stream-buffer-size* (* 1024 256))

(defn streams-for-out
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
  [^Session session ^String cmd in out {:keys [sudo] :as opts}]
  (when debug
    (prn 'ssh-exec session)
    (println cmd)
    (println in)
    (prn out opts))
  (let [[^PipedOutputStream out-stream
         ^PipedInputStream out-inputstream] (streams-for-out out)
        [^PipedOutputStream err-stream
         ^PipedInputStream err-inputstream] (streams-for-out out)

        in (if (:required? sudo)
             (spire.sudo/prefix-sudo-stdin sudo in)
             in)

        cmd (if sudo
              (spire.sudo/make-sudo-command sudo "" cmd)
              cmd)

        ;;_ (prn 'ssh-exec 'in in 'cmd cmd)

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

(defn wait-for-channel-exit [channel]
  (.getExitStatus ^com.jcraft.jsch.ChannelExec channel))
