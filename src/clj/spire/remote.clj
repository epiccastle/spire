(ns spire.remote
  (:require [spire.nio :as nio]
            [spire.facts :as facts]
            [clojure.string :as string]))

(set! *warn-on-reflection* true)

(defn is-writable? [run path]
  (->> (facts/on-os
        :freebsd (run (format "if ( -w \"%s\" ) then\necho write\nelse\necho no\nendif\n" path))
        :else (run (format "if [ -w \"%s\" ]; then echo write; else echo no; fi" path)))
       string/trim
       (= "write")))

(defn is-readable? [run path]
  (->> (facts/on-os
        :freebsd (run (format "if ( -r \"%s\" ) then\necho read\nelse\necho no\nendif\n" path))
        :else (run (format "if [ -r \"%s\" ]; then echo read; else echo no; fi" path)))
       string/trim
       (= "read")))

(defn is-file? [run path]
  (->> (facts/on-os
        :freebsd (run (format "if ( -f \"%s\" ) then\necho file\nelse\necho not\nendif\n" path))
        :else (run (format "if [ -f \"%s\" ]; then echo file; else echo not; fi" path)))
       string/trim
       (= "file")))

(defn is-dir? [run path]
  (->> (facts/on-os
        :freebsd (run (format "if ( -d \"%s\" ) then\necho dir\nelse\necho not\nendif\n" path))
        :else (run (format "if [ -d \"%s\" ]; then echo dir; else echo not; fi" path)))
       string/trim
       (= "dir")))

(defn exists? [run path]
  (->> (facts/on-os
        :freebsd (run (format "if ( -e \"%s\" ) then\necho exists\nelse\necho not\nendif\n" path))
        :else (run (format "if [ -e \"%s\" ]; then echo exists; else echo not; fi" path)))
       string/trim
       (= "exists")))

(defn process-md5-out
  ([line]
   (process-md5-out (facts/get-fact [:system :os]) line))
  ([os line]
   (cond
     (#{:linux} os)
     (vec (reverse (string/split line #"\s+" 2)))

     :else
     (let [[_ filename hash] (re-matches #"MD5\s+\((.+)\)\s*=\s*([0-9a-fA-F]+)" line)]
       [filename hash])
     )))


(defn path-md5sums
  [run path]
  (let [find-result (run (format "find \"%s\" -type f -exec \"%s\" {} \\;" path (facts/md5)))]
    (when (pos? (count find-result))
      (some-> find-result
              string/split-lines
              (->> (map process-md5-out)
                   (map (fn [[fname hash]] [(nio/relativise path fname) hash]))
                   (into {}))))))

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

(defn process-stat-mode-out
  ([mode-string]
   (process-stat-mode-out (facts/os) mode-string))
  ([os mode-string]
   (cond
     (#{:linux} os)
     mode-string

     :else
     (subs mode-string (- (count mode-string) 3)))))

(defn path-full-info [run path]
  (let [file-info
        (-> (facts/on-os
             :linux (run (format "find \"%s\" -type f -exec stat -c '%%a %%X %%Y %%s' {} \\; -exec md5sum {} \\;" path))
             :else (run (format "find \"%s\" -type f -exec stat -f '%%p %%a %%m %%z' {} \\; -exec md5 {} \\;" path))
             )
            (string/split #"\n")
            (->> (partition 2)
                 (map (fn [[stats hashes]]
                        (let [[mode last-access last-modified size] (string/split stats #" " 4)
                              [filename md5sum] (process-md5-out hashes)
                              fname (nio/relativise path filename)
                              mode (process-stat-mode-out mode)]
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
        (-> (facts/on-os
             :linux (run (format "find \"%s\" -type d -exec stat -c '%%a %%X %%Y %%s %%n' {} \\;" path))
             :else (run (format "find \"%s\" -type d -exec stat -f '%%p %%a %%m %%z %%N' {} \\;" path))
             )
            (string/split #"\n")
            (->> (filter #(pos? (count %)))
                 (map (fn [stats]
                        (let [[mode last-access last-modified size filename] (string/split stats #" " 5)
                              fname (nio/relativise path filename)
                              mode (process-stat-mode-out mode)]
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

#_ (transport/ssh
 "root@192.168.92.237"
 ;;"root@localhost"
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
   (path-full-info run "/root")))

(defn make-temp-filename
  "makes a temporary filename that is correct for the present remote host"
  [& [{:keys [prefix extension directory]}]]
  (let [rand-string (string/join "" (map (fn [_] (rand-nth "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")) (range 16)))]
    (format "%s/%s%s.%s" (or directory "/tmp") (or prefix "") rand-string (or extension "dat"))))

#_ (make-temp-filename {:prefix "foo-"
                        :extension "txt"
                        :directory "/tmp"})
#_ (make-temp-filename {:prefix "foo-"
                        :extension "txt"})
#_ (make-temp-filename {:prefix "foo-"
                        :directory "/opt"})
