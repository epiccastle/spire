(ns spire.remote
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.nio :as nio]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn is-file? [run path]
  (->> (run (format "if [ -f \"%s\" ]; then echo file; else echo dir; fi" path))
       string/trim
       (= "file")))

(defn path-md5sums [run path]
  (let [find-result (run (format "find \"%s\" -type f -exec md5sum {} \\;" path))]
    (when (pos? (count find-result))
      (some-> find-result
              string/split-lines
              (->> (map #(vec (reverse (string/split % #"\s+" 2))))
                   (map (fn [[fname hash]] [(nio/relativise path fname) hash]))
                   (into {}))))))

#_ (defn runner []
  (fn [cmd] (->> cmd
                 (clojure.java.shell/sh "bash" "-c")
                 :out)))

#_
(file-md5sums (runner) "test")


(defn path-full-info [run path]
  (-> (run (format "find \"%s\" -type f -exec stat -c '%%a %%X %%Y %%s' {} \\; -exec md5sum {} \\;" path))
      (string/split #"\n")
      (->> (partition 2)
           (map (fn [[stats hashes]]
                  (let [[mode last-access last-modified size] (string/split stats #" " 4)
                        [md5sum filename] (string/split hashes #"\s+" 2)
                        fname (nio/relativise path filename)]
                    [fname {:filename fname
                            :md5sum md5sum
                            :mode-string mode
                            :mode (Integer/parseInt mode 8)
                            :last-access (Integer/parseInt last-access)
                            :last-modified (Integer/parseInt last-modified)
                            :size (Integer/parseInt size)
                            }])))
           (into {}))))

#_
(path-full-info (runner) "test")
