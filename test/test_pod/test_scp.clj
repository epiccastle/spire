(ns test-pod.test-scp
  (:require [test-pod.utils :refer [bash]]
            [clojure.test :refer [is deftest]]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.scp :as scp]
            [pod.epiccastle.spire.ssh :as ssh]
            [pod.epiccastle.spire.local :as local]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest scp-to-local
  (transport/ssh "localhost"
                 (bash "echo foo > /tmp/test.txt")
                 (bash "rm -rf /tmp/scp-dest")
                 (bash "mkdir /tmp/scp-dest ")
                 (scp/scp-to
                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                  :exec :local
                  :exec-fn local/local-exec)
                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))))

(deftest scp-to-ssh
  (transport/ssh "localhost"
                 (bash "rm /tmp/scp-dest/test.txt")
                 (scp/scp-to
                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))))

(deftest scp-to-ssh-recurse
  (transport/ssh "localhost"
                 (bash "rm /tmp/scp-dest/test.txt")
                 (bash "rm -rf /tmp/scp-src")
                 (bash "mkdir -p /tmp/scp-src/test1/empty")
                 (bash "echo foo > /tmp/scp-src/foo")
                 (bash "echo foo > /tmp/scp-src/test1/foo")
                 (scp/scp-to
                  state/connection ["/tmp/scp-src"] "/tmp/scp-dest"
                  :recurse true
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (is (= "foo\n" (slurp "/tmp/scp-dest/scp-src/foo")))
                 (is (= "foo\n" (slurp "/tmp/scp-dest/scp-src/test1/foo")))
                 (is (.isDirectory (io/file "/tmp/scp-dest/scp-src/test1/empty")))))

(deftest scp-content-string-to-local
  (transport/ssh "localhost"
                 (bash "rm -rf /tmp/scp-dest/")
                 (bash "mkdir -p /tmp/scp-dest")
                 (scp/scp-content-to
                  state/connection "foo" "/tmp/scp-dest/bar"
                  :exec :local
                  :exec-fn local/local-exec)
                 (is (= "foo" (slurp "/tmp/scp-dest/bar")))))

(deftest scp-content-bytes-to-local
  (transport/ssh "localhost"
                 (bash "rm -rf /tmp/scp-dest/")
                 (bash "mkdir -p /tmp/scp-dest")
                 (scp/scp-content-to
                  state/connection (.getBytes "foo") "/tmp/scp-dest/bar"
                  :exec :local
                  :exec-fn local/local-exec)
                 (is (= "foo" (slurp "/tmp/scp-dest/bar")))))

(deftest scp-content-string-to-ssh
  (transport/ssh "localhost"
                 (bash "rm -rf /tmp/scp-dest/")
                 (bash "mkdir -p /tmp/scp-dest")
                 (scp/scp-content-to
                  state/connection "foo" "/tmp/scp-dest/bar"
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (is (= "foo" (slurp "/tmp/scp-dest/bar")))))

(deftest scp-content-bytes-to-ssh
  (transport/ssh "localhost"
                 (bash "rm -rf /tmp/scp-dest/")
                 (bash "mkdir -p /tmp/scp-dest")
                 (scp/scp-content-to
                  state/connection (.getBytes "foo") "/tmp/scp-dest/bar"
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (is (= "foo" (slurp "/tmp/scp-dest/bar")))))

(deftest scp-from-ssh
  (transport/ssh "localhost"
                 (bash "rm -rf /tmp/scp-src/ /tmp/scp-dest")
                 (bash "mkdir -p /tmp/scp-src/ /tmp/scp-dest")
                 (bash "echo foo > /tmp/scp-src/foo")
                 (scp/scp-from
                  state/connection
                  ["/tmp/scp-src/foo"]
                  "/tmp/scp-dest"
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (is (= "foo\n" (slurp "/tmp/scp-dest/foo")))))

(deftest scp-from-local
  (transport/ssh "localhost"
                 (bash "rm -rf /tmp/scp-src/ /tmp/scp-dest")
                 (bash "mkdir -p /tmp/scp-src/ /tmp/scp-dest")
                 (bash "echo foo > /tmp/scp-src/foo")
                 (scp/scp-from
                  state/connection
                  ["/tmp/scp-src/foo"]
                  "/tmp/scp-dest"
                  :exec :local
                  :exec-fn local/local-exec)
                 (is (= "foo\n" (slurp "/tmp/scp-dest/foo")))))

(deftest scp-from-local-recurse
  (transport/ssh "localhost"
                 (bash "rm -rf /tmp/scp-src")
                 (bash "mkdir -p /tmp/scp-src/test1/empty")
                 (bash "echo foo > /tmp/scp-src/foo")
                 (bash "echo foo > /tmp/scp-src/test1/foo")
                 (scp/scp-from
                  state/connection
                  ["/tmp/scp-src"]
                  "/tmp/scp-dest"
                  :recurse true
                  :exec :local
                  :exec-fn local/local-exec)
                 (is (= "foo\n" (slurp "/tmp/scp-dest/scp-src/foo")))
                 (is (= "foo\n" (slurp "/tmp/scp-dest/scp-src/test1/foo")))
                 (is (.isDirectory (io/file "/tmp/scp-dest/scp-src/test1/empty")))))

(deftest scp-from-ssh-recurse
  (transport/ssh "localhost"
                 (bash "rm -rf /tmp/scp-src")
                 (bash "mkdir -p /tmp/scp-src/test1/empty")
                 (bash "echo foo > /tmp/scp-src/foo")
                 (bash "echo foo > /tmp/scp-src/test1/foo")
                 (scp/scp-from
                  state/connection
                  ["/tmp/scp-src"]
                  "/tmp/scp-dest"
                  :recurse true
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (is (= "foo\n" (slurp "/tmp/scp-dest/scp-src/foo")))
                 (is (= "foo\n" (slurp "/tmp/scp-dest/scp-src/test1/foo")))
                 (is (.isDirectory (io/file "/tmp/scp-dest/scp-src/test1/empty")))))
