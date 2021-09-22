(ns test-pod.test-remote
  (:require [test-pod.utils :refer [run bash]]
            [clojure.string :as string]
            [clojure.test :refer [is deftest]]

            [pod.epiccastle.spire.remote :as remote]))

(deftest is-writeable?
  (bash "rm -rf /tmp/foo")
  (is (not (remote/is-writable? bash "/tmp/foo")))
  (bash "touch /tmp/foo")
  (is (remote/is-writable? bash "/tmp/foo"))
  (bash "chmod a-w /tmp/foo")
  (is (not (remote/is-writable? bash "/tmp/foo")))
  (bash "chmod a+w /tmp/foo")
  )

(deftest is-readable?
  (bash "rm -rf /tmp/foo")
  (is (not (remote/is-readable? bash "/tmp/foo")))
  (bash "touch /tmp/foo")
  (is (remote/is-readable? bash "/tmp/foo"))
  (bash "chmod a-r /tmp/foo")
  (is (not (remote/is-readable? bash "/tmp/foo")))
  (bash "chmod a+r /tmp/foo")
  )

(deftest is-file?
  (bash "rm -rf /tmp/foo")
  (is (not (remote/is-file? bash "/tmp/foo")))
  (bash "touch /tmp/foo")
  (is (remote/is-file? bash "/tmp/foo"))
  (is (not (remote/is-file? bash "/tmp/"))))

(deftest is-dir?
  (bash "rm -rf /tmp/foo")
  (is (not (remote/is-dir? bash "/tmp/foo")))
  (bash "touch /tmp/foo")
  (is (not (remote/is-dir? bash "/tmp/foo")))
  (is (remote/is-dir? bash "/tmp/")))

(deftest exists?
  (bash "rm -rf /tmp/foo")
  (is (not (remote/exists? bash "/tmp/foo")))
  (bash "touch /tmp/foo")
  (is (remote/exists? bash "/tmp/foo"))
  (is (remote/exists? bash "/tmp/")))

(deftest process-md5-out
  (is (= ["/tmp/foo\n" "d41d8cd98f00b204e9800998ecf8427e"]
         (remote/process-md5-out (bash "md5sum /tmp/foo")))))

(deftest path-md5sums
  (bash "rm -rf /tmp/foo")
  (bash "mkdir -p /tmp/foo/bar/baz")
  (bash "touch /tmp/foo/1")
  (bash "touch /tmp/foo/bar/2")
  (bash "touch /tmp/foo/bar/baz/3")
  (is (=
       {"bar/baz/3" "d41d8cd98f00b204e9800998ecf8427e"
        "bar/2" "d41d8cd98f00b204e9800998ecf8427e"
        "1" "d41d8cd98f00b204e9800998ecf8427e"}
       (remote/path-md5sums bash "/tmp/foo"))))

(deftest process-stat-mode-out
  (is (= "644"  (remote/process-stat-mode-out :linux "644"))))

(deftest path-full-info
  (bash "rm -rf /tmp/foo")
  (bash "mkdir -p /tmp/foo/bar/baz")
  (bash "touch /tmp/foo/1")
  (bash "touch /tmp/foo/bar/2")
  (bash "touch /tmp/foo/bar/baz/3")
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
   is))

(deftest make-temp-filename
  (let [filename (remote/make-temp-filename)]
    (is (string/starts-with? filename "/tmp/"))
    (is (string/ends-with? filename ".dat"))))
