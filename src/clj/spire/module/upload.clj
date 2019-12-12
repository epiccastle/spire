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
  (let [find-result (run (format "find \"%s\" -type f -exec md5sum {} \\;" dest))]
    (when (pos? (count find-result))
      (some-> find-result
              string/split-lines
              (->> (map #(vec (reverse (string/split % #"\s+" 2))))
                   (map (fn [[fname hash]] [(utils/relativise dest fname) hash]))
                   ;;(map vec)
                   ;;(mapv reverse)
                   (into {})
                   )))))



(defn runner []
  (fn [cmd] (->> cmd
                 (clojure.java.shell/sh "bash" "-c")
                 :out)))

#_
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

(defn gather-file-sizes [src local-files identical-files]
  (let [file-set (clojure.set/difference (into #{} local-files) (into #{} identical-files))]
    (into {}
          (for [f file-set]
            [f (.length (io/file src f))]
            ))
    )
  )

#_
(gather-file-sizes "test" (keys (md5-local-dir "test")) '("files/copy/test.txt"))

(defn compare-local-and-remote [local-path remote-runner remote-path]
  (let [local-md5-files (md5-local-dir local-path)
        remote-md5-files (md5-remote-dir remote-runner remote-path)
        local-file? (.isFile (io/file local-path))
        remote-file? (and (= 1 (count remote-md5-files))
                          (= '("") (keys remote-md5-files)))
        identical-files (if (or local-file? remote-file?)
                          #{}
                          (->> (same-files local-md5-files remote-md5-files)
                               (filter identity)
                               (map #(.getPath (io/file local-path %)))
                               (into #{})))
        local-to-remote (->> local-md5-files
                             (map first)
                             (filter (complement identical-files))
                             (into #{}))
        remote-to-local (->> remote-md5-files
                             (map first)
                             (filter (complement identical-files))
                             (into #{}))
        local-to-remote-filesizes (->> local-to-remote
                                       (map (fn [f] [f (.length (io/file local-path f))]))
                                       (into {}))
        local-to-remote-total-size (->> local-to-remote-filesizes
                                        (map second)
                                        (apply +))


        ]
    {:local-md5 local-md5-files
     :remote-md5 remote-md5-files
     :local-file? local-file?
     :remote-file? remote-file?
     :identical identical-files
     :local-to-remote local-to-remote
     :remote-to-local remote-to-local
     :local-to-remote-filesizes local-to-remote-filesizes
     :local-to-remote-total-size local-to-remote-total-size
     :local-to-remote-max-filename-length (apply max
                                                 (map #(count (str (io/file local-path %)))
                                                      local-to-remote))
     }
    ))

#_
(compare-local-and-remote "test" (runner) "/tmp/spire")

#_
(md5-remote-dir (runner) "/tmp/spire")



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

              transfers (compare-local-and-remote (str src) run dest)

              {:keys [remote-file? identical local-md5 local-to-remote-max-filename-length
                      local-to-remote-filesizes local-to-remote-total-size local-to-remote]}
              transfers

              copied?
              (if ;;recurse
                  (utils/content-recursive? src)
                ;; recursive copy
                  (cond
                    (and remote-file? (not force))
                    {:result :failed :err "Cannot copy `src` directory over `dest`: destination is a file. Use :force to delete destination file and replace."}

                    (and remote-file? force)
                    (do
                      (run (format "rm -f \"%s\"" dest))
                      (scp/scp-to session src dest
                                  :progress-fn (fn [& args]
                                                 (output/print-progress
                                                  host-string
                                                  (apply utils/progress-stats args)))
                                  :preserve preserve
                                  :dir-mode (or dir-mode 0755)
                                  :mode (or mode 644)
                                  :recurse true

                                  :fileset-total local-to-remote-total-size
                                  :max-filename-length local-to-remote-max-filename-length
                                  ))

                    (not remote-file?)
                    (let [identical-files identical]
                      (when (not= (count identical-files) (count local-md5))
                        (scp/scp-to session src dest
                                    :progress-fn (fn [& args]
                                                   (output/print-progress
                                                    host-string
                                                    (apply utils/progress-stats args)
                                                    ))
                                    :preserve preserve
                                    :dir-mode (or dir-mode 0755)
                                    :mode (or mode 0644)
                                    :recurse true
                                    :skip-files identical-files
                                    :fileset-total local-to-remote-total-size
                                    :max-filename-length local-to-remote-max-filename-length
                                    ))))

                  ;; straight copy
                  (let [local-md5 (digest/md5 src)
                        remote-md5 (some-> (run (format "%s -b \"%s\"" "md5sum" dest))
                                           (string/split #"\s+")
                                           first)]
                    (when (not= local-md5 remote-md5)
                      (scp/scp-to session src dest
                                  :progress-fn
                                  (fn [& args]
                                    (output/print-progress
                                     host-string args
                                     {src (.length (io/file src))}))
                                  :preserve preserve
                                  :dir-mode (or dir-mode 0755)
                                  :mode (or mode 0644)
                                  :fileset-total (.length (io/file src))
                                  :max-filename-length (count (str src))
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
