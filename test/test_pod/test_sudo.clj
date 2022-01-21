(ns test-pod.test-sudo
  (:require [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            ))

;; (require '[babashka.pods :as pods])

;; (pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.scp :as scp]
         '[pod.epiccastle.spire.ssh :as ssh]
         '[pod.epiccastle.spire.local :as local]
         '[pod.epiccastle.spire.transport :as transport]
         '[pod.epiccastle.spire.state :as state]
         '[pod.epiccastle.spire.module.sudo :as sudo]
         '[pod.epiccastle.spire.module.shell :as shell]
         )

(def username (or (System/getenv "TEST_USER") (System/getenv "USER")))
(def sudo-password (System/getenv "TEST_SUDO_PASSWORD"))

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

(deftest sudo-to-root-local
       (transport/local
                      (sudo/sudo-user {:password sudo-password}
                                      ;;(prn state/shell-context)
                                      ;; (prn state/connection)

                                      (let [result
                                            (local/local-exec
                                             nil "whoami" "" "UTF-8"
                                             {:sudo (:sudo state/shell-context)})]
                                        (is (= 0 (:exit result)))
                                        (is (= "root\n" (:out result)))))))

(deftest sudo-to-root-ssh
       (transport/ssh "localhost"
                      (sudo/sudo-user {:password sudo-password}
                                      ;; (prn state/shell-context)
                                      ;; (prn state/connection)

                                      (let [result
                                            (ssh/ssh-exec
                                             state/connection "whoami" "" "UTF-8"
                                             {:sudo (:sudo state/shell-context)})]
                                        (is (= 0 (:exit result)))
                                        (is (= "root\n" (:out result)))
                                        (is (clojure.string/starts-with? (:err result) "[sudo] password for"))))))


(defn preflight []
  (bash "sudo rm /tmp/test.txt /tmp/scp-dest/")
  (bash "echo foo > /tmp/test.txt")
  (bash "sudo chown root:root /tmp/test.txt")
  (bash "rm -rf /tmp/scp-dest")
  (bash "mkdir /tmp/scp-dest ")
  )

(deftest sudo-to-root-scp-transport-ssh
  (transport/ssh {:username username
                  :hostname "localhost"}
                 (sudo/sudo-user {:password sudo-password}
                                 (preflight)
                                 (scp/scp-to
                                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"

                                  :exec :ssh
                                  :exec-fn ssh/ssh-exec

                                  :sudo (:sudo state/shell-context))

                                 (is (= "root\n" (bash "stat -c %U /tmp/scp-dest/test.txt")))
                                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                                 )

                 ))

(deftest sudo-to-root-scp-transport-local
  (transport/local
   (sudo/sudo-user {:password sudo-password}
                   (preflight)
                   (scp/scp-to
                    state/connection ["/tmp/test.txt"] "/tmp/scp-dest"

                    :exec :local
                    :exec-fn local/local-exec

                    :sudo (:sudo state/shell-context))

                   (is (= "root\n" (bash "stat -c %U /tmp/scp-dest/test.txt")))
                   (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                   )

   ))



#_(deftest no-sudo-to-root-scp-ssh
  (transport/ssh "crispin@localhost"
                 (prn state/shell-context)
                                 ;; (prn state/


                 (bash "sudo rm /tmp/test.txt /tmp/scp-dest/")
                 (bash "echo foo > /tmp/test.txt")
                 (bash "sudo chown root:root /tmp/test.txt")
                 (bash "rm -rf /tmp/scp-dest")
                 (bash "mkdir /tmp/scp-dest ")
                 (scp/scp-to
                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"

                  :exec :ssh
                  :exec-fn ssh/ssh-exec
                  ;; :exec :local
                  ;; :exec-fn local/local-exec

                  :sudo (:sudo state/shell-context))

                 (is (= "crispin\n" (bash "stat -c %U /tmp/scp-dest/test.txt")))
                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))


                 ))

#_
(deftest sudo-to-root-scp-ssh-user-level
  (transport/ssh "crispin@localhost"
                 (sudo/sudo-user {:password sudo-password
                                  :username "crispin"}
                                 (prn state/shell-context)
                                 ;; (prn state/

                                 (bash "sudo rm /tmp/test.txt /tmp/scp-dest/")
                                 (bash "echo foo > /tmp/test.txt")
                                 (bash "sudo chown root:root /tmp/test.txt")
                                 (bash "rm -rf /tmp/scp-dest")
                                 (bash "mkdir /tmp/scp-dest ")
                                 (scp/scp-to
                                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"

                                  :exec :ssh
                                  :exec-fn ssh/ssh-exec
                                  ;; :exec :local
                                  ;; :exec-fn local/local-exec

                                  :sudo (:sudo state/shell-context))

                                 (is (= "crispin\n" (bash "stat -c %U /tmp/scp-dest/test.txt")))
                                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                                 )

                 ))
