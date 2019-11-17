(ns spire.core
  (:require [spire.shell :as shell]
            [spire.probe :as probe]
            [spire.utils :as utils]
            [spire.config :as config]
            [puget.printer :as puget]
            [digest :as digest]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.cli :as cli])
  (:gen-class))

(defmacro embed [filename]
  (slurp filename))

(def version (embed ".meta/VERSION"))

(def cli-options
  [
   ["-h" "--help" "Print the command line help"]
   ["-v" "--version" "Print the version string and exit"]
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
      (do (println "Running server version " version)
          (Thread/sleep 5000)
          (println "done"))

      :else
      (let [host-string (or (first arguments) "localhost")
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

        (puget/cprint
         (shell/run proc (format "%s --server" spire-dest)))

        #_(puget/cprint {:commands commands
                         :lsb-release (probe/lsb-release proc)
                         :github-reachable? (probe/reach-website? proc commands "http://github.com")}))
      )


    )

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
