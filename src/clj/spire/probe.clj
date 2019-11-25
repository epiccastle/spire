(ns spire.probe
  (:require [spire.utils :as utils]
            [clojure.string :as string]))

(defn- make-run [ssh-runner]
  (fn [command]
    (let [{:keys [out exit]} (ssh-runner command "" "" {})]
      (when (zero? exit)
        (string/trim out)))))

(defn commands [runner]
  (let [run (make-run runner)
        which #(run (str "which " %))
        paths {:md5sum (which "md5sum")
               :crc32 (which "crc32")
               :sha256sum (which "sha256sum")
               :curl (which "curl")
               :wget (which "wget")
               :ping (which "ping")}]
    paths))

(defn lsb-release [runner]
  (let [run (make-run runner)]
    (some-> "lsb_release -a" run utils/lsb-process)))

(defn reach-website? [runner {:keys [curl wget]} url]
  (let [run (make-run runner)
        {:keys [exit]}
        (run
          (cond
            curl (format "%s -I \"%s\"" curl url)
            wget (format "%s -S --spider \"%s\"" wget url)))]
    (zero? exit)))
