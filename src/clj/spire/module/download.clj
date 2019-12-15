(ns spire.module.download
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.module.attrs :as attrs]
            [spire.compare :as compare]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn preflight [opts]
  nil)

(utils/defmodule download [{:keys [src dest recurse preserve flat dir-mode mode] :as opts}]
  [host-string session]
  (or
   (preflight opts)
   (let [run (fn [command]
               (let [{:keys [out exit]}
                     (ssh/ssh-exec session command "" "UTF-8" {})]
                 (when (zero? exit)
                   (string/trim out))))

         {:keys [remote-to-local local-file?] :as comparison}
         (compare/compare-local-and-remote dest run src)
         ]
     (if recurse
       (cond
         (and local-file? (not force))
         {:result :failed :err "Cannot copy remote `src` over local `dest`: destination is a file. Use :force to delete destination file and replace."}

         (and local-file? force)
         (do
           (.delete (io/file dest))
           (scp/scp-from session src dest
                         :progress-fn (fn [file bytes total frac context]
                                        #_ (output/print-progress
                                         host-string
                                         (utils/progress-stats
                                          file bytes total frac
                                          local-to-remote-total-size
                                          local-to-remote-max-filename-length
                                          context)
                                         ))
                         :preserve preserve
                         :dir-mode (or dir-mode 0755)
                         :mode (or mode 0644)
                         :recurse true
                         :skip-files #{}))


         ))
     #_ (scp/scp-from session src dest :recurse recurse :preserve true
                      ))))
