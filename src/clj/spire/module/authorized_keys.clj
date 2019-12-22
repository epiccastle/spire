(ns spire.module.authorized-keys
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.local :as local]
            [spire.remote :as remote]
            [spire.compare :as compare]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn preflight [command {:keys [user state key options path] :as opts}]
  nil
  )

(utils/defmodule authorized-key [command {:keys [user key options path] :as opts}]
  [host-string session]
  (or
   (preflight opts)
   (let [run (fn [command]
               (let [{:keys [out exit err]}
                     (ssh/ssh-exec session command "" "UTF-8" {})]
                 (comment
                   (println "exit:" exit)
                   (println "out:" out)
                   (println "err:" err))
                 (if (zero? exit)
                   (string/trim out)
                   "")))


         ])
   )

  )
