(ns test-pod.test-remote
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            ))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.remote :as remote])

(def username (or (System/getenv "TEST_USER") (System/getenv "USER")))

(defn run [args]
  (-> args process :out slurp))

(defn run-trim [args]
  (-> args run string/trim))

(defn run-int [args]
  (-> args run-trim Integer/parseInt))

(defn stat-mode [file]
  ;; gnu stat format. linux only
  (run-trim ["stat" "-c" "%a" file]))

(defn stat-last-modified-time [file]
  (run-int ["stat" "-c" "%Y" file]))

(defn stat-last-access-time [file]
  (run-int ["stat" "-c" "%X" file]))

(defn bash [command]
  (run ["bash" "-c" command]))

(run ["rm" "-rf" "/tmp/foo"])
(assert (not (remote/is-writable? bash "/tmp/foo")))
(run ["touch" "/tmp/foo"])
(assert (remote/is-writable? bash "/tmp/foo"))
(run ["chmod" "a-w" "/tmp/foo"])
(assert (not (remote/is-writable? bash "/tmp/foo")))

(run ["rm" "-f" "/tmp/foo"])
(assert (not (remote/is-readable? bash "/tmp/foo")))
(run ["touch" "/tmp/foo"])
(assert (remote/is-readable? bash "/tmp/foo"))
(run ["chmod" "a-r" "/tmp/foo"])
(assert (not (remote/is-readable? bash "/tmp/foo")))

(run ["rm" "-f" "/tmp/foo"])
(assert (not (remote/is-file? bash "/tmp/foo")))
(run ["touch" "/tmp/foo"])
(assert (remote/is-file? bash "/tmp/foo"))
(assert (not (remote/is-file? bash "/tmp/")))

(run ["rm" "-f" "/tmp/foo"])
(assert (not (remote/is-dir? bash "/tmp/foo")))
(run ["touch" "/tmp/foo"])
(assert (not (remote/is-dir? bash "/tmp/foo")))
(assert (remote/is-dir? bash "/tmp/"))

(run ["rm" "-f" "/tmp/foo"])
(assert (not (remote/exists? bash "/tmp/foo")))
(run ["touch" "/tmp/foo"])
(assert (remote/exists? bash "/tmp/foo"))
(assert (remote/exists? bash "/tmp/"))

(assert (= ["/tmp/foo\n" "d41d8cd98f00b204e9800998ecf8427e"]
           (remote/process-md5-out (bash "md5sum /tmp/foo"))))

(bash "rm -rf /tmp/foo")
(bash "mkdir -p /tmp/foo/bar/baz")
(bash "touch /tmp/foo/1")
(bash "touch /tmp/foo/bar/2")
(bash "touch /tmp/foo/bar/baz/3")

(assert (=
         {"bar/baz/3" "d41d8cd98f00b204e9800998ecf8427e"
          "bar/2" "d41d8cd98f00b204e9800998ecf8427e"
          "1" "d41d8cd98f00b204e9800998ecf8427e"}
         (remote/path-md5sums bash "/tmp/foo")))

(assert (= "644"  (remote/process-stat-mode-out :linux "644")))


(->>
 (remote/path-full-info bash "/tmp/foo")
 (mapv (fn [[k v]] [k (select-keys v [:mode-string :mode :type :filename])]))
 (into {})
 (= {"" {:mode-string "755"
         :mode 493
         :type :dir
         :filename "foo"}
     "bar" {:mode-string "755"
            :mode 493
            :type :dir
            :filename "bar"}
     "bar/baz" {:mode-string "755"
                :mode 493
                :type :dir
                :filename "bar/baz"}
     "bar/baz/3" {:mode-string "644"
                  :mode 420
                  :type :file
                  :filename "bar/baz/3"}
     "bar/2" {:mode-string "644"
              :mode 420
              :type :file
              :filename "bar/2"}
     "1" {:mode-string "644"
          :mode 420
          :type :file
          :filename "1"}})
 assert)

(let [filename (remote/make-temp-filename)]
  (assert (string/starts-with? filename "/tmp/"))
  (assert (string/ends-with? filename ".dat")))
