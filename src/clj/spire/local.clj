(ns spire.local
  (:require [spire.nio :as nio]
            [digest :as digest]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defn is-file? [path]
  (.isFile (io/file path)))

(defn path-md5sums [path]
  (->> (file-seq (io/file path))
       (filter #(.isFile ^java.io.File %))
       (map (fn [f] [(nio/relativise path f) (digest/md5 f)]))
       (into {})))

#_ (md5-local-dir "test/")
#_ (file-seq (io/file "test/"))

(defn path-full-info [path]
  (->> (file-seq (io/file path))
       (map (fn [f]
              (cond
                (.isFile ^java.io.File f)
                (let [filename (nio/relativise path f)
                      mode (nio/file-mode f)]
                  [filename
                   {
                    :type :file
                    :filename filename
                    :md5sum (digest/md5 f)
                    :mode mode
                    :mode-string (format "%o" mode)
                    :last-access (nio/last-access-time f)
                    :last-modified (nio/last-modified-time f)
                    :size (.length ^java.io.File f)
                    }])

                (.isDirectory ^java.io.File f)
                (let [filename (nio/relativise path f)
                      mode (nio/file-mode f)]
                  [filename
                   {
                    :type :dir
                    :filename filename
                    :mode mode
                    :mode-string (format "%o" mode)
                    :last-access (nio/last-access-time f)
                    :last-modified (nio/last-modified-time f)
                    }]))))
       (into {})))

#_ (path-full-info "/tmp/bashrc")
