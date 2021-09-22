(ns test-pod.test-scp
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            ))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.scp :as scp]
         '[pod.epiccastle.spire.ssh :as ssh]
         '[pod.epiccastle.spire.local :as local]
         '[pod.epiccastle.spire.transport :as transport]
         '[pod.epiccastle.spire.state :as state]
         )

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

(transport/ssh "localhost"

               (comment
                 (bash "echo foo > /tmp/test.txt")
                 (bash "rm -rf /tmp/scp-dest")
                 (bash "mkdir /tmp/scp-dest ")
                 (scp/scp-to
                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                  :exec :local
                  :exec-fn local/local-exec)
                 (assert (= "foo\n" (slurp "/tmp/scp-dest/test.txt"))))

               (comment
                 (bash "rm /tmp/scp-dest/test.txt")
                 (scp/scp-to
                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (assert (= "foo\n" (slurp "/tmp/scp-dest/test.txt"))))

               (comment
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
                 (assert (= "foo\n" (slurp "/tmp/scp-dest/scp-src/foo")))
                 (assert (= "foo\n" (slurp "/tmp/scp-dest/scp-src/test1/foo")))
                 (assert (.isDirectory (io/file "/tmp/scp-dest/scp-src/test1/empty"))))

               (comment
                 (bash "rm -rf /tmp/scp-dest/")
                 (bash "mkdir -p /tmp/scp-dest")
                 (scp/scp-content-to
                  state/connection "foo" "/tmp/scp-dest/bar"
                  :exec :local
                  :exec-fn local/local-exec)
                 (assert (= "foo" (slurp "/tmp/scp-dest/bar"))))

               (comment
                 (bash "rm -rf /tmp/scp-dest/")
                 (bash "mkdir -p /tmp/scp-dest")
                 (scp/scp-content-to
                  state/connection (.getBytes "foo") "/tmp/scp-dest/bar"
                  :exec :local
                  :exec-fn local/local-exec)
                 (assert (= "foo" (slurp "/tmp/scp-dest/bar"))))

               (comment
                 (bash "rm -rf /tmp/scp-dest/")
                 (bash "mkdir -p /tmp/scp-dest")
                 (scp/scp-content-to
                  state/connection "foo" "/tmp/scp-dest/bar"
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (assert (= "foo" (slurp "/tmp/scp-dest/bar"))))

               (comment
                 (bash "rm -rf /tmp/scp-dest/")
                 (bash "mkdir -p /tmp/scp-dest")
                 (scp/scp-content-to
                  state/connection (.getBytes "foo") "/tmp/scp-dest/bar"
                  :exec :ssh
                  :exec-fn ssh/ssh-exec)
                 (assert (= "foo" (slurp "/tmp/scp-dest/bar"))))

               (bash "rm -rf /tmp/scp-src/ /tmp/scp-dest")
               (bash "mkdir -p /tmp/scp-src/ /tmp/scp-dest")
               (bash "echo foo > /tmp/scp-src/foo")
               (scp/scp-from
                state/connection
                ["/tmp/scp-src/foo"]
                "/tmp/scp-dest"
                :exec :ssh
                :exec-fn ssh/ssh-exec)
               (assert (= "foo\n" (slurp "/tmp/scp-dest/foo")))

               (bash "rm -rf /tmp/scp-src/ /tmp/scp-dest")
               (bash "mkdir -p /tmp/scp-src/ /tmp/scp-dest")
               (bash "echo foo > /tmp/scp-src/foo")
               (scp/scp-from
                state/connection
                ["/tmp/scp-src/foo"]
                "/tmp/scp-dest"
                :exec :local
                :exec-fn local/local-exec)
               (assert (= "foo\n" (slurp "/tmp/scp-dest/foo")))

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
               (assert (= "foo\n" (slurp "/tmp/scp-dest/scp-src/foo")))
               (assert (= "foo\n" (slurp "/tmp/scp-dest/scp-src/test1/foo")))
               (assert (.isDirectory (io/file "/tmp/scp-dest/scp-src/test1/empty")))

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
               (assert (= "foo\n" (slurp "/tmp/scp-dest/scp-src/foo")))
               (assert (= "foo\n" (slurp "/tmp/scp-dest/scp-src/test1/foo")))
               (assert (.isDirectory (io/file "/tmp/scp-dest/scp-src/test1/empty")))

               )
