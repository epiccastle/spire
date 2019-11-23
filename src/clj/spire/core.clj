(ns spire.core
  (:require [spire.ssh :as ssh]
            [spire.known-hosts :as known-hosts]
            [spire.ssh-agent :as ssh-agent]
            [spire.config :as config]
            [spire.namespaces :as namespaces]
            [puget.printer :as puget]
            [digest :as digest]
            [sci.core :as sci]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli]
            [edamame.core :as edamame])
  (:import [com.jcraft.jsch JSch UserInfo]
           [java.io
            File InputStream OutputStream StringReader
            FileInputStream FileOutputStream
            ByteArrayInputStream ByteArrayOutputStream
            PipedInputStream PipedOutputStream]
           [com.jcraft.jsch
            JSch Session Channel ChannelShell ChannelExec ChannelSftp JSchException
            Identity IdentityFile IdentityRepository Logger KeyPair LocalIdentityRepository
            HostKeyRepository HostKey]
           [org.apache.commons.codec.binary Base64]
           [org.apache.commons.codec.digest HmacUtils HmacAlgorithms]

           )

  (:gen-class))

(defmacro embed [filename]
  (slurp filename))

(def version (embed ".meta/VERSION"))

(def cli-options
  [
   ["-h" "--help" "Print the command line help"]
   ["-v" "--version" "Print the version string and exit"]
   ["-e" "--evaluate CODE" "Evaluate the code passed in on the command line" ]
   [nil "--server" "Run in server mode on the other end of the connection"]])

(defn initialise []
  (config/init!)
  (System/loadLibrary "spire"))

(defn usage [options-summary]
  (->> ["Pragmatic Provisioning"
        ""
        "Usage: spire [options] username@hostname"
        ""
        "Options:"
        options-summary]
       (string/join \newline)))

(defn- to-camel-case [^String a]
  (apply str (map string/capitalize (.split (name a) "-"))))

(defn ^Session make-session
  "Start a SSH session.
Requires hostname.  You can also pass values for :username, :password and :port
keys.  All other option key pairs will be passed as SSH config options."
  [^JSch agent hostname
   {:keys [port username password] :or {port 22} :as options}]
  (let [username (or username (System/getProperty "user.name"))
        session-options (dissoc options :username :port :password :agent)
        session (.getSession agent username hostname port)]
    (when password (.setPassword session password))
    (doseq [[k v] options]
      (.setConfig session (to-camel-case k) (name v)))
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
    :doc (str "The buffer size (in bytes) for the piped stream used to implement
    the :stream option for :out. If your ssh commands generate a high volume of
    output, then this buffer size can become a bottleneck. You might also
    increase the frequency with which you read the output stream if this is an
    issue.")}
  *piped-stream-buffer-size* (* 1024 10))

(defn- streams-for-out
  [out]
  (if (= :stream out)
    (let [os (PipedOutputStream.)]
      [os (PipedInputStream. os (int *piped-stream-buffer-size*))])
    [(ByteArrayOutputStream.) nil]))

(defn string-stream
  "Return an input stream with content from the string s."
  [^String s]
  {:pre [(string? s)]}
  (ByteArrayInputStream. (.getBytes s utf-8)))

(defn ssh-exec
  "Run a command via ssh-exec."
  [^Session session ^String cmd in out opts]
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
    (if out-inputstream
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
                  (.toString err-stream))}))))


(defn -main
  [& args]
  (initialise)
  #_ (ssh/open-auth-socket)
  (let [agent (JSch.)

        session (make-session agent "localhost" {:username "crispin"
                                                 ;;:strict-host-key-checking :yes ;;:no
                                                 })

        ;;_ (.setConfig session "PreferredAuthentications" "publickey")

        irepo (ssh-agent/make-identity-repository)

        _ (when (System/getenv "SSH_AUTH_SOCK")
            (.setIdentityRepository session irepo))

        user-info (ssh/make-user-info)

        host-key-repo (known-hosts/make-host-key-repository)
        ]

    (.setHostKeyRepository session host-key-repo)
    (.setUserInfo session user-info)

    (.connect session)
    (println (ssh-exec session "whoami" "" "" {}))
    (.disconnect session)

    )
  )

(defn -other-main
  "ssh to ourselves and collect paths"
  [& args]
  (initialise)
  (let [{:keys [options summary arguments]} (cli/parse-opts args cli-options)]
    (cond
      (:help options)
      (println (usage summary))

      (:version options)
      (println "Version:" version)

      (:server options)
      (doseq [line (line-seq (java.io.BufferedReader. *in*))]
        (try
          (-> line
              (sci/eval-string {:namespaces namespaces/namespaces
                                :bindings namespaces/bindings})
              pr)
          (catch Exception e
            (binding [*out* *err*]
              (pr e))))
        (println "---end-stdout---")
        (binding [*out* *err*]
          (println "---end-stderr---")))

      (:evaluate options)
      (let [script (->> options :evaluate)]
        (-> script
            (sci/eval-string {:namespaces namespaces/namespaces
                              :bindings namespaces/bindings})
            puget/cprint))

      (pos? (count arguments))
      (let [script (slurp (first arguments))]
        (-> script
            (sci/eval-string {:namespaces namespaces/namespaces
                              :bindings namespaces/bindings})
            puget/cprint))

      :else
      ;; repl
      (puget/cprint 0)

      #_ (let [host-string (or (first arguments) "localhost")
               proc (shell/proc ["ssh" host-string])
               snapshot? (string/ends-with? version "-SNAPSHOT")
               release? (not snapshot?)
               commands  (probe/commands proc)
               local-spire (utils/which-spire)
               local-spire-digest (digest/md5 (io/as-file local-spire))
               spire-dest (str "/tmp/spire-" local-spire-digest)
               ]
           ;; sync binary
           (utils/push commands proc host-string local-spire spire-dest)

           (shell/feed-from-string proc (format "%s --server\n" spire-dest))
           (.write *out* "> ")
           (.flush *out*)
           (doseq [line (line-seq (java.io.BufferedReader. *in*))]
             (shell/feed-from-string proc (str line "\n"))
             (let [[_ out] (shell/capture-until (:out-reader proc) "---end-stdout---\n")
                   [_ err] (shell/capture-until (:err-reader proc) "---end-stderr---\n")]
               (puget/cprint {:out (edamame/parse-string out)
                              :err (when (pos? (count err))
                                     (edamame/parse-string (subs err 6)))})
               (.write *out* "> ")
               (.flush *out*)
               ))

           #_ (shell/run proc (format "echo \"(+ 1 2 3)\" | %s --server" spire-dest))

           #_(puget/cprint {:commands commands
                            :lsb-release (probe/lsb-release proc)
                            :github-reachable? (probe/reach-website? proc commands "http://github.com")}))))

  #_ (let [host-string (or (first args) "localhost")
           proc (sh/proc ["ssh" host-string])
           run (fn [command]
                 (let [{:keys [out exit]} (sh/run proc command)]
                   (when (zero? exit)
                     (string/trim out))))
           which #(run (str "which " %))
           paths
           {:md5sum (which "md5sum")
            :crc32 (which "crc32")
            :sha1sum (which "sha1sum")
            :sha256sum (which "sha256sum")
            :sha512sum (which "sha512sum")
            :sha224sum (which "sha224sum")
            :sha384sum (which "sha384sum")
            :curl (which "curl")
            :wget (which "wget")
            :apt (which "apt")
            :apt-get (which "apt-get")
            :apt-cache (which "apt-cache")
            :rpm (which "rpm")
            :yum (which "yum")
            }

           lsb-release (some-> "lsb_release -a" run utils/lsb-process)
           spire-md5 (digest/md5 (io/as-file "./spire"))
           remote-path (str "/tmp/spire-" spire-md5)
           spire-remote (some-> (run (str "md5sum -b " remote-path))
                                (string/split #"\s+")
                                first)
           properties  (into {} (map (fn [[k v]] [(keyword k) v]) (System/getProperties)))
           ]
       (when (or (not spire-remote) (not= spire-md5 spire-remote))
         (sh/copy-with-progress "./spire" host-string remote-path utils/progress-bar)
         (println))

       (puget/cprint {:spire-local spire-md5
                      :spire-remote spire-remote
                      :paths paths
                      :lsb-release lsb-release
                      :properties properties
                      :bin (utils/exe-bin-path)})

       ))
