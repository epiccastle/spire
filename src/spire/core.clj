(ns spire.core
  (:require [spire.shell :as sh]
            [puget.printer :as puget]
            [clojure.string :as string])
  (:gen-class))

(defn -main
  "ssh to ourselves and collect paths"
  [& args]
  (let [proc (sh/proc ["ssh" (or (first args) "localhost")])
        which (fn [command]
                (let [{:keys [out exit]} (sh/run proc (str "which " command))]
                  (when (zero? exit)
                    (string/trim out))))
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
         }]
    (puget/cprint paths)))
