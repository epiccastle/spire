(ns spire.local
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn is-file? [path]
  (.isFile (io/file path)))

(defn path-md5sums [path]
  (->> (file-seq (io/file path))
       (filter #(.isFile %))
       (map (fn [f] [(utils/relativise path f) (digest/md5 f)]))
       (into {})))

#_ (md5-local-dir "test/")
#_ (file-seq (io/file "test/"))

(defn path-full-info [path]
  (->> (file-seq (io/file path))
       (filter #(.isFile %))
       (map (fn [f]
              (let [filename (utils/relativise path f)
                    mode (utils/file-mode f)]
                [filename
                 {
                  :filename filename
                  :md5sum (digest/md5 f)
                  :mode mode
                  :mode-string (format "%o" mode)
                  :last-access (utils/last-access-time f)
                  :last-modified (utils/last-modified-time f)
                  :size (.length f)
                  }])))
       (into {})))

#_ (path-full-info "/tmp/bashrc")
