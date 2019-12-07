(ns spire.module.copy
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(utils/defmodule copy [{:keys [src dest]}]
  [host-string session]
  (let [run (fn [command]
              (let [{:keys [out exit]}
                    (ssh/ssh-exec session command "" "UTF-8" {})]
                (when (zero? exit)
                  (string/trim out))))
        local-md5 (digest/md5 (io/as-file src))
        remote-md5 (some-> (run (format "%s -b \"%s\"" "md5sum" dest))
                           (string/split #"\s+")
                           first)]
    (if (= local-md5 remote-md5)
      {:result :ok}
      (do
        (scp/scp-to session src dest :progress-fn (fn [& args] (output/print-progress host-string args)))
        {:result :changed}))))
