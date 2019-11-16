(ns spire.probe
  (:require [spire.utils :as utils]
            [spire.shell :as shell]
            [clojure.string :as string]))

(defn- make-run [ssh-proc]
  (fn [command]
    (let [{:keys [out exit]} (shell/run ssh-proc command)]
      (when (zero? exit)
        (string/trim out)))))

(defn commands [ssh-proc]
  (let [run (make-run ssh-proc)
        which #(run (str "which " %))
        paths {:md5sum (which "md5sum")
               :crc32 (which "crc32")
               :sha256sum (which "sha256sum")
               :curl (which "curl")
               :wget (which "wget")
               :ping (which "ping")}]
    paths))

(defn lsb-release [ssh-proc]
  (let [run (make-run ssh-proc)]
    (some-> "lsb_release -a" run utils/lsb-process)))

(defn reach-website? [ssh-proc {:keys [curl wget]} url]
  (let [{:keys [exit]}
        (shell/run ssh-proc
          (cond
            curl (format "%s -I \"%s\"" curl url)
            wget (format "%s -S --spider \"%s\"" wget url)))]
    (zero? exit)))
