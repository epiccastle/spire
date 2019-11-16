(ns spire.core
  (:require [spire.shell :as sh]
            [spire.utils :as utils]
            [puget.printer :as puget]
            [digest :as digest]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:gen-class))

(defn -main
  "ssh to ourselves and collect paths"
  [& args]
  (System/loadLibrary "spire")
  (let [host-string (or (first args) "localhost")
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
                   :bin (utils/exe-bin-path)})))
