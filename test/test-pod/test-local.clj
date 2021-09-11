(ns test-pod.test-local
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            ))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.local :as local])

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

(bash "rm -rf /tmp/foo")
(assert (not (local/is-file? "/tmp/foo")))
(bash "touch /tmp/foo")
(assert (local/is-file? "/tmp/foo"))
(assert (not (local/is-dir? "/tmp/foo")))

(bash "rm -rf /tmp/foo")
(assert (not (local/is-dir? "/tmp/foo")))
(bash "mkdir /tmp/foo")
(assert (local/is-dir? "/tmp/foo"))
(assert (not (local/is-file? "/tmp/foo")))

(bash "rm -rf /tmp/foo")
(assert (not (local/is-readable? "/tmp/foo")))
(assert (not (local/is-writable? "/tmp/foo")))
(bash "touch /tmp/foo")
(bash "chmod a-r+w /tmp/foo")
(assert (not (local/is-readable? "/tmp/foo")))
(assert (local/is-writable? "/tmp/foo"))
(bash "chmod a+r-w /tmp/foo")
(assert (local/is-readable? "/tmp/foo"))
(assert (not (local/is-writable? "/tmp/foo")))

(bash "rm -rf /tmp/foo")
(bash "mkdir -p /tmp/foo/bar/baz")
(bash "touch /tmp/foo/1")
(bash "touch /tmp/foo/bar/2")
(bash "touch /tmp/foo/bar/baz/3")

(assert (=
         (local/path-md5sums "/tmp/foo")
         {"bar/baz/3" "d41d8cd98f00b204e9800998ecf8427e"
          "bar/2" "d41d8cd98f00b204e9800998ecf8427e"
          "1" "d41d8cd98f00b204e9800998ecf8427e"}))

(->>
 (local/path-full-info "/tmp/foo")
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

(assert (= "foo\n" (:out (local/local-exec nil "echo foo" nil  "UTF-8" {}))))
