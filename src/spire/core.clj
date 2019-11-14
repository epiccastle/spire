(ns spire.core
  (:require [spire.shell :as sh]
            [puget.printer :as puget]
            [clojure.string :as string])
  (:gen-class))

(defn to-camelcase [s]
  (-> s
      (string/split #"\s+")
      (->> (map string/lower-case)
           (string/join "-"))))

(defn lsb-process [out]
  (-> out
      (string/split #"\n")
      (->> (map #(string/split % #":\s+"))
           (map (fn [[k v]] [(-> k to-camelcase keyword) v]))
           (into {}))))

(defn -main
  "ssh to ourselves and collect paths"
  [& args]
  (let [proc (sh/proc ["ssh" (or (first args) "localhost")])
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

        lsb-release (some-> "lsb_release -a" run lsb-process)]
    (puget/cprint {:paths paths
                   :lsb-release lsb-release})))
