(ns test-pod.test-local
  (:require [test-pod.utils :refer [bash]]
            [clojure.string :as string]
            [clojure.test :refer [is deftest]]

            [pod.epiccastle.spire.local :as local]
            ))

(deftest is-file?
  (bash "rm -rf /tmp/foo")
  (is (not (local/is-file? "/tmp/foo")))
  (bash "touch /tmp/foo")
  (is (local/is-file? "/tmp/foo"))
  (is (not (local/is-dir? "/tmp/foo"))))

(deftest is-dir?
  (bash "rm -rf /tmp/foo")
  (is (not (local/is-dir? "/tmp/foo")))
  (bash "mkdir /tmp/foo")
  (is (local/is-dir? "/tmp/foo"))
  (is (not (local/is-file? "/tmp/foo"))))

(deftest is-readable-writable?
  (bash "rm -rf /tmp/foo")
  (is (not (local/is-readable? "/tmp/foo")))
  (is (not (local/is-writable? "/tmp/foo")))
  (bash "touch /tmp/foo")
  (bash "chmod a-r+w /tmp/foo")
  (is (not (local/is-readable? "/tmp/foo")))
  (is (local/is-writable? "/tmp/foo"))
  (bash "chmod a+r-w /tmp/foo")
  (is (local/is-readable? "/tmp/foo"))
  (is (not (local/is-writable? "/tmp/foo"))))

(deftest path-md5sums
  (bash "rm -rf /tmp/foo")
  (bash "mkdir -p /tmp/foo/bar/baz")
  (bash "touch /tmp/foo/1")
  (bash "touch /tmp/foo/bar/2")
  (bash "touch /tmp/foo/bar/baz/3")
  (is (=
       (local/path-md5sums "/tmp/foo")
       {"bar/baz/3" "d41d8cd98f00b204e9800998ecf8427e"
        "bar/2" "d41d8cd98f00b204e9800998ecf8427e"
        "1" "d41d8cd98f00b204e9800998ecf8427e"})))

(deftest path-full-info
  (bash "rm -rf /tmp/foo")
  (bash "mkdir -p /tmp/foo/bar/baz")
  (bash "touch /tmp/foo/1")
  (bash "touch /tmp/foo/bar/2")
  (bash "touch /tmp/foo/bar/baz/3")
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
   is))

(deftest local-exec
  (is (= "foo\n" (:out (local/local-exec nil "echo foo" nil  "UTF-8" {}))))
  (let [{:keys [out-stream]} (local/local-exec nil "echo -n foo" nil :stream {})]
    (is (= (mapv (fn [_] (.read out-stream)) (range 5))
           [102 111 111 -1 -1]))))
