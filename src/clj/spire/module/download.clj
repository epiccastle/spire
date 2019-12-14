(ns spire.module.download
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.module.attrs :as attrs]
            [spire.module.upload :as upload]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn preflight [opts]
  nil)

(utils/defmodule download [{:keys [src dest recurse preserve flat] :as opts}]
  [host-string session]
  (or
   (preflight opts)
   (let [run (fn [command]
               (let [{:keys [out exit]}
                     (ssh/ssh-exec session command "" "UTF-8" {})]
                 (when (zero? exit)
                   (string/trim out))))

         {:keys [remote-to-local] :as comparison}
         (upload/compare-local-and-remote dest run src)
         ]
     (scp/scp-from session src dest :recurse recurse :preserve true
                   ))))
