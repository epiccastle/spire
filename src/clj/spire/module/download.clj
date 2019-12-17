(ns spire.module.download
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.local :as local]
            [spire.remote :as remote]
            [spire.module.attrs :as attrs]
            [spire.compare :as compare]
            [digest :as digest]
            [puget.printer :as puget]
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

         ;; analyse local and remote paths
         local-file? (local/is-file? dest)
         remote-file? (remote/is-file? run src)

         {:keys [remote-to-local identical-content remote] :as comparison}
         (compare/compare-full-info

          ;; scp from a directory will create that directory on local
          (if remote-file?
            dest
            (io/file dest (.getName (io/file src))))

          run src)

         {:keys [sizes total]} (compare/remote-to-local comparison)

         max-filename-length (->> remote-to-local
                                  (map count)
                                  (apply max 0))
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
                                        (output/print-progress
                                         host-string
                                         (utils/progress-stats
                                          file bytes total frac
                                          total
                                          max-filename-length
                                          context)
                                         ))
                         :preserve preserve
                         :dir-mode (or dir-mode 0755)
                         :mode (or mode 0644)
                         :recurse true
                         :skip-files #{}))

         (not local-file?)
         (do
           ;;(println "---")
           ;;(println identical-content)
           ;;(println remote)
           (when (not= (count identical-content) (count remote))
             ;;(println "///")
             (scp/scp-from session src dest
                           :progress-fn (fn [file bytes total frac context]
                                          #_(output/print-progress
                                           host-string
                                           (utils/progress-stats
                                            file bytes total frac
                                            total
                                            max-filename-length
                                            context)))
                           :preserve preserve
                           :dir-mode (or dir-mode 0755)
                           :mode (or mode 0644)
                           :recurse true
                           :skip-files identical-content
                           )))
         )


       )
     #_ (scp/scp-from session src dest :recurse recurse :preserve true
                      ))))
