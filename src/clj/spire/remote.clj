(ns spire.remote
  (:require [spire.output :as output]
            [spire.ssh :as ssh]
            [spire.scp :as scp]
            [spire.utils :as utils]
            [spire.nio :as nio]
            [spire.transport :as transport]
            [spire.facts :as facts]
            [spire.module.attrs :as attrs]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn is-file? [run path]
  (->> (run (format "if [ -f \"%s\" ]; then echo file; else echo dir; fi" path))
       string/trim
       (= "file")))

(defn process-md5-out [os line]
  (cond
    (#{:freebsd} os)
    (let [[_ filename hash] (re-matches #"MD5\s+\((.+)\)\s*=\s*([0-9a-fA-F]+)" line)]
      [filename hash])

    :else
    (vec (reverse (string/split line #"\s+" 2)))))

(defn path-md5sums [run path]
  (let [os (facts/get-fact [:system :os])
        md5 (facts/get-fact [:paths :md5])
        md5sum (facts/get-fact [:paths :md5sum])
        md5path (or md5sum md5)]
    (let [find-result (run (format "find \"%s\" -type f -exec \"%s\" {} \\;" path md5path))]
      (when (pos? (count find-result))
        (some-> find-result
                string/split-lines
                (->> (map #(process-md5-out os %))
                     (map (fn [[fname hash]] [(nio/relativise path fname) hash]))
                     (into {})))))))

#_(transport/ssh
 ;;"root@192.168.92.237"
 "root@localhost"
 (let [run (fn [command]
             (let [{:keys [out exit err]}
                   (ssh/ssh-exec spire.state/*connection* command "" "UTF-8" {})]
               (comment
                 (println "exit:" exit)
                 (println "out:" out)
                 (println "err:" err))
               (if (zero? exit)
                 (string/trim out)
                 "")))]
   (path-md5sums run "/root")))

#_ (defn runner []
  (fn [cmd] (->> cmd
                 (clojure.java.shell/sh "bash" "-c")
                 :out)))

#_
(file-md5sums (runner) "test")


(defn path-full-info [run path]
  (let [file-info
        (-> (run (format "find \"%s\" -type f -exec stat -c '%%a %%X %%Y %%s' {} \\; -exec md5sum {} \\;" path))
            (string/split #"\n")
            (->> (partition 2)
                 (map (fn [[stats hashes]]
                        (let [[mode last-access last-modified size] (string/split stats #" " 4)
                              [md5sum filename] (string/split hashes #"\s+" 2)
                              fname (nio/relativise path filename)]
                          [fname {:type :file
                                  :filename fname
                                  :md5sum md5sum
                                  :mode-string mode
                                  :mode (Integer/parseInt mode 8)
                                  :last-access (Integer/parseInt last-access)
                                  :last-modified (Integer/parseInt last-modified)
                                  :size (Integer/parseInt size)
                                  }])))
                 (into {})))

        dir-info
        (-> (run (format "find \"%s\" -type d -exec stat -c '%%a %%X %%Y %%s %%n' {} \\;" path))
            (string/split #"\n")
            (->> (filter #(pos? (count %)))
                 (map (fn [stats]
                        (let [[mode last-access last-modified size filename] (string/split stats #" " 5)
                              fname (nio/relativise path filename)]
                          [fname {:type :dir
                                  :filename fname
                                  :mode-string mode
                                  :mode (Integer/parseInt mode 8)
                                  :last-access (Integer/parseInt last-access)
                                  :last-modified (Integer/parseInt last-modified)}])))
                 (into {})))]
    (into dir-info file-info)))

#_
(path-full-info (runner) "test")
