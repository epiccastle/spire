(ns test-pod.test-nio
  (:require [test-pod.conf :refer [username]]
            [test-pod.utils :refer
             [bash run-int run-trim
              stat-mode stat-last-modified-time stat-last-access-time]]
            [clojure.test :refer [is deftest]]

            [pod.epiccastle.spire.nio :as nio]))

(def user-id (run-int ["id" "-u"]))
(def group-id (run-int ["id" "-g"]))
(def group-name (run-trim ["id" "-gn"]))

(deftest basic-files
  (is (= "../bar" (nio/relativise "/path/to/foo" "/path/to/bar")))
  (is (< 1599000000 (nio/last-access-time ".")))
  (is (< 1599000000 (nio/last-modified-time ".")))
  (is (<= 0 (nio/file-mode ".") 0777))
  (is (= 3 (count (nio/mode->permissions 0700)))))

(deftest modes
  (bash "rm -rf /tmp/foo")
  (nio/create-file "/tmp/foo" 0666)
  ;; assume umask 022
  (is (= "644" (stat-mode "/tmp/foo")))
  (nio/set-file-mode "/tmp/foo" 0777)
  (is (= "777" (stat-mode "/tmp/foo"))))

(deftest touch
  (is (= "2020-09-01 22:40:00.000000000 +0000" (nio/timestamp->touch 1599000000)))
  (is (= "202009012240.00" (nio/timestamp->touch-bsd 1599000000))))

(deftest times
  (bash "rm -rf /tmp/foo")
  (nio/create-file "/tmp/foo" 0777)

  (nio/set-last-modified-time "/tmp/foo" 1599000000)
  (is (= 1599000000 (stat-last-modified-time "/tmp/foo")))

  (nio/set-last-access-time "/tmp/foo" 1599000000)
  (is (= 1599000000 (stat-last-access-time "/tmp/foo")))

  (nio/set-last-modified-and-access-time "/tmp/foo" 1599000001 1599000002)
  (is (= 1599000001 (stat-last-modified-time "/tmp/foo")))
  (is (= 1599000002 (stat-last-access-time "/tmp/foo")))

  (is (nio/idem-set-last-access-time "/tmp/foo" 1599000000))
  (is (= 1599000000 (stat-last-access-time "/tmp/foo")))
  (is (not (nio/idem-set-last-access-time "/tmp/foo" 1599000000)))
  (is (= 1599000000 (stat-last-access-time "/tmp/foo")))

  (is (nio/idem-set-last-modified-time "/tmp/foo" 1599000000))
  (is (= 1599000000 (stat-last-modified-time "/tmp/foo")))
  (is (not (nio/idem-set-last-modified-time "/tmp/foo" 1599000000)))
  (is (= 1599000000 (stat-last-modified-time "/tmp/foo"))))

(deftest ownership
  (bash "rm -rf /tmp/foo")
  (nio/create-file "/tmp/foo" 0777)

  (is (nio/set-owner "/tmp/foo" username))

  (is (nio/set-owner "/tmp/foo" user-id))

  (nio/set-owner "/tmp/foo" username)
  (is (not (nio/idem-set-owner "/tmp/foo" username)))
  (nio/set-owner "/tmp/foo" user-id)
  (is (not (nio/idem-set-owner "/tmp/foo" user-id))))

(deftest groups
  (bash "rm -rf /tmp/foo")
  (nio/create-file "/tmp/foo" 0777)

  (is (nio/set-group "/tmp/foo" group-name))
  (is (not (nio/idem-set-group "/tmp/foo" group-name)))
  (is (nio/set-group "/tmp/foo" group-id))
  (is (not (nio/idem-set-group "/tmp/foo" group-id))))

(deftest more-modes
  (bash "rm -rf /tmp/foo")
  (nio/create-file "/tmp/foo" 0777)

  (is (nio/idem-set-mode "/tmp/foo" 0400))
  (is (= "400" (stat-mode "/tmp/foo")))
  (is (not (nio/idem-set-mode "/tmp/foo" 0400)))
  (is (= "400" (stat-mode "/tmp/foo")))

  (is (nio/idem-set-mode "/tmp/foo" 0666))
  (is (= "666" (stat-mode "/tmp/foo")))
  (is (not (nio/idem-set-mode "/tmp/foo" 0666)))
  (is (= "666" (stat-mode "/tmp/foo")))

  (is (not (nio/set-attr "/tmp/foo" user-id group-id 0666)))
  (is (nio/set-attr "/tmp/foo" user-id group-id 0400))

  (is (nio/set-attrs {:path "/tmp/foo" :mode 0644}))
  (is (not (nio/set-attrs {:path "/tmp/foo" :mode 0644}))))

(deftest set-attrs-preserve
  (bash "rm -rf /tmp/bar")
  (bash "mkdir -p /tmp/bar/baz")
  (bash "touch /tmp/bar/foo")
  (bash "touch /tmp/bar/baz/foo")
  (is
   (nio/set-attrs-preserve
    {"foo" {:mode 0666 :last-access 1599000000 :last-modified 1599000000}
     "baz" {:mode 0777 :last-access 1599000000 :last-modified 1599000000}
     "baz/foo" {:mode 0666 :last-access 1599000000 :last-modified 1599000000}}
    "/tmp/bar"))

  (is (= 1599000000 (stat-last-modified-time "/tmp/bar/foo")))
  (is (= 1599000000 (stat-last-modified-time "/tmp/bar/baz")))
  (is (= 1599000000 (stat-last-modified-time "/tmp/bar/baz/foo")))
  (is (= 1599000000 (stat-last-access-time "/tmp/bar/foo")))
  (is (= 1599000000 (stat-last-access-time "/tmp/bar/baz")))
  (is (= 1599000000 (stat-last-access-time "/tmp/bar/baz/foo")))
  (is (= "666" (stat-mode "/tmp/bar/foo")))
  (is (= "777" (stat-mode "/tmp/bar/baz")))
  (is (= "666" (stat-mode "/tmp/bar/baz/foo"))))
