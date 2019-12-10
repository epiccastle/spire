(ns spire.module.upload
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn preflight [opts]


  )

(defn process-result [opts result]
  result
  )

(defn md5-local-dir [src]
  (->> (file-seq (io/file src))
       (filter #(.isFile %))
       (map (fn [f] [(utils/relativise src f) (digest/md5 f)]))
       (into {})))


#_ (md5-local-dir "test/")
#_ (file-seq (io/file "test/"))

(defn md5-remote-dir [run dest]
  (some-> (run (format "find \"%s\" -type f -exec md5sum {} \\;" dest))
          string/split-lines
          (->> (map #(vec (reverse (string/split % #"\s+" 2))))
               (map (fn [[fname hash]] [(utils/relativise dest fname) hash]))
               ;;(map vec)
               ;;(mapv reverse)
               (into {})
               )))



(defn runner []
  (fn [cmd] (->> cmd
                 (clojure.java.shell/sh "bash" "-c")
                 :out)))

(md5-remote-dir (runner) "test")


(defn md5-file [run dest]
  (some-> (run (format "%s -b \"%s\"" "md5sum" dest))
          (string/split #"\s+")
          first))

(defn same-files [local-md5s remote-md5s]
  (->> (for [[f md5] local-md5s]
         (when (= md5 (get remote-md5s f))
           f))
       (filterv identity)))

#_ (same-files
    (md5-local-dir "test")
    (md5-remote-dir (runner) "/tmp/spire")
    )

(utils/defmodule upload [{:keys [src dest
                                 owner group mode attrs
                                 dir-mode preserve recurse force]
                          :as opts}]
  [host-string session]
  (or
   (preflight opts)
   (->> (let [run (fn [command]
                    (let [{:keys [out exit]}
                          (ssh/ssh-exec session command "" "UTF-8" {})]
                      (when (zero? exit)
                        (string/trim out))))

              copied?
              (if ;;recurse
                  (utils/content-recursive? src)
                ;; recursive copy
                  (let [local-md5-files (md5-local-dir src)
                        remote-md5-files (md5-remote-dir run dest)
                        remote-is-file? (and (= 1 (count remote-md5-files))
                                             (= '("") (keys remote-md5-files)))]
                    (cond
                      (and remote-is-file? (not force))
                      {:result :failed :err "Cannot copy `src` directory over `dest`: destination is a file. Use :force to delete destination file and replace."}

                      (and remote-is-file? force)
                      (do
                        (run (format "rm -f \"%s\"" dest))
                        (scp/scp-to session src dest
                                    :progress-fn (fn [& args] (output/print-progress host-string args))
                                    :preserve preserve
                                    :dir-mode (or dir-mode 0755)
                                    :mode (or mode 644)
                                    :recurse true))

                      (not remote-is-file?)
                      (let [identical-files (->> (same-files local-md5-files remote-md5-files)
                                                 (map #(.getPath (io/file src %)))
                                                 (into #{}))]
                        (when (not= (count identical-files) (count local-md5-files))
                          (scp/scp-to session src dest
                                      :progress-fn (fn [& args] (output/print-progress host-string args))
                                      :preserve preserve
                                      :dir-mode (or dir-mode 0755)
                                      :mode (or mode 0644)
                                      :recurse true
                                      :skip-files identical-files)))))

                  ;; straight copy
                  (let [local-md5 (digest/md5 src)
                        remote-md5 (some-> (run (format "%s -b \"%s\"" "md5sum" dest))
                                           (string/split #"\s+")
                                           first)]
                    (when (not= local-md5 remote-md5)
                      (scp/scp-to session src dest
                                  :progress-fn (fn [& args] (output/print-progress host-string args))
                                  :preserve preserve
                                  :dir-mode (or dir-mode 0755)
                                  :mode (or mode 0644)
                                  ))))

              passed-attrs? (or owner group mode attrs)]
          (if (not passed-attrs?)
            ;; just copied
            [copied? nil]

            ;; run attrs
            [copied? (attrs/set-attrs
                      session
                      {:path dest
                       :owner owner
                       :group group
                       :mode mode
                       :attrs attrs
                       :recurse recurse})]))

        #_ (process-result opts))))
