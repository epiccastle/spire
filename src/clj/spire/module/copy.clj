(ns spire.module.copy
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(utils/defmodule copy [{:keys [src dest owner group mode attrs]}]
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
    (let [copied?
          (if (= local-md5 remote-md5)
            false
            (do
              (scp/scp-to session src dest :progress-fn (fn [& args] (output/print-progress host-string args)))
              true))
          passed-attrs? (or owner group mode attrs)]
      (if (not passed-attrs?)
        ;; just copied
        {:result (if copied? :changed :ok)}

        ;; run attrs
        (let [{:keys [exit err out] :as result}
              (attrs/set-attrs
               session
               {:path dest
                :owner owner
                :group group
                :mode mode
                :attrs attrs})]
          (cond
            (zero? exit)
            {:result (if copied? :changed :ok)}

            (= 255 exit)
            (assoc result :result :changed)

            :else
            (assoc result :result :failed)))))))
