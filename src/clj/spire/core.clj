(ns spire.core
  (:require [spire.shell :as shell]
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

(defn -main
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
