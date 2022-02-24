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

;;
;; sudo execute
;;
(deftest sudo-to-root-local
       (transport/local
                      (sudo/sudo-user {:password sudo-password}
                                      (let [result
                                            (local/local-exec
                                             nil "whoami" "" "UTF-8"
                                             {:sudo (:sudo state/shell-context)})]
                                        (is (= 0 (:exit result)))
                                        (is (= "root\n" (:out result)))))))

(deftest sudo-to-root-ssh
       (transport/ssh "localhost"
                      (sudo/sudo-user {:password sudo-password}
                                      (let [result
                                            (ssh/ssh-exec
                                             state/connection "whoami" "" "UTF-8"
                                             {:sudo (:sudo state/shell-context)})]
                                        (is (= 0 (:exit result)))
                                        (is (= "root\n" (:out result)))
                                        (is (clojure.string/starts-with? (:err result) "[sudo] password for"))))))

;;
;; sudo scp-to
;;
(defn setup []
  (bash "sudo rm -rf /tmp/test.txt /tmp/scp-dest/")
  (bash "echo foo > /tmp/test.txt")
  (bash "sudo chown root:root /tmp/test.txt")
  (bash "rm -rf /tmp/scp-dest")
  (bash "mkdir /tmp/scp-dest ")
  )

(defn teardown []
  (bash "sudo rm -rf /tmp/test.txt /tmp/scp-dest/"))

(deftest sudo-to-root-scp-transport-ssh
  (transport/ssh {:username username
                  :hostname "localhost"}
                 (sudo/sudo-user {:password sudo-password}
                                 (setup)
                                 (scp/scp-to
                                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                                  :exec :ssh
                                  :exec-fn ssh/ssh-exec
                                  :sudo (:sudo state/shell-context))
                                 (is (= "root\n" (bash "stat -c %U /tmp/scp-dest/test.txt")))
                                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                                 (teardown)

                                 )

                 ))

(deftest sudo-to-root-scp-transport-local
  (transport/local
   (sudo/sudo-user {:password sudo-password}
                   (setup)
                   (scp/scp-to
                    state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                    :exec :local
                    :exec-fn local/local-exec
                    :sudo (:sudo state/shell-context))
                   (is (= "root\n" (bash "stat -c %U /tmp/scp-dest/test.txt")))
                   (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                   (teardown))))



(deftest no-sudo-to-root-scp-transport-ssh
  (transport/ssh "localhost"
                 (setup)
                 (scp/scp-to
                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                  :exec :ssh
                  :exec-fn ssh/ssh-exec
                  :sudo (:sudo state/shell-context))
                 (is (= (str username "\n") (bash "stat -c %U /tmp/scp-dest/test.txt")))
                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                 (teardown)))

(deftest no-sudo-to-root-scp-transport-local
  (transport/local
   (setup)
   (scp/scp-to
    state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
    :exec :local
    :exec-fn local/local-exec
    :sudo (:sudo state/shell-context))
   (is (= (str username "\n") (bash "stat -c %U /tmp/scp-dest/test.txt")))
   (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
   (teardown)))

(deftest sudo-to-root-scp-user-level-transport-ssh
  (transport/ssh "localhost"
                 (sudo/sudo-user {:password sudo-password
                                  :username username}
                                 (setup)
                                 (scp/scp-to
                                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                                  :exec :ssh
                                  :exec-fn ssh/ssh-exec
                                  :sudo (:sudo state/shell-context))
                                 (is (= (str username "\n") (bash "stat -c %U /tmp/scp-dest/test.txt")))
                                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                                 (teardown))))

(deftest sudo-to-root-scp-user-level-transport-local
  (transport/local
   (sudo/sudo-user {:password sudo-password
                    :username username}
                   (setup)
                   (scp/scp-to
                    state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                    :exec :local
                    :exec-fn local/local-exec
                    :sudo (:sudo state/shell-context))
                   (is (= (str username "\n") (bash "stat -c %U /tmp/scp-dest/test.txt")))
                   (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                   (teardown))))

;;
;; sudo scp-from
;;
(defn setup2 []
  (setup)
  (bash "sudo chmod 0700 /tmp/test.txt")
  )

(deftest sudo-to-root-scp-from-transport-ssh
  (transport/ssh {:username username
                  :hostname "localhost"}
                 (sudo/sudo-user {:password sudo-password}
                                 (setup2)
                                 (scp/scp-from
                                  state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                                  :exec :ssh
                                  :exec-fn ssh/ssh-exec
                                  :sudo (:sudo state/shell-context))
                                 (is (= (str username "\n") (bash "stat -c %U /tmp/scp-dest/test.txt")))
                                 (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                                 (teardown)
                                 )

                 ))

(deftest sudo-to-root-scp-from-transport-ssh-check-restricted
  (transport/ssh {:username username
                  :hostname "localhost"}
                 (setup2)
                 (is
                  (thrown? clojure.lang.ExceptionInfo #"Pipe closed"
                           (scp/scp-from
                            state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                            :exec :ssh
                            :exec-fn ssh/ssh-exec
                            :sudo (:sudo state/shell-context))))
                 (teardown)))


(deftest sudo-to-root-scp-from-transport-local
  (transport/local
   (sudo/sudo-user {:password sudo-password}
                   (setup2)
                   (scp/scp-from
                    state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                    :exec :local
                    :exec-fn local/local-exec
                    :sudo (:sudo state/shell-context))
                   (is (= "crispin\n" (bash "stat -c %U /tmp/scp-dest/test.txt")))
                   (is (= "foo\n" (slurp "/tmp/scp-dest/test.txt")))
                   (teardown))))


(deftest sudo-to-root-scp-from-transport-local-check-restricted
  (transport/local
   (setup2)
   (scp/scp-from
    state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
    :exec :local
    :exec-fn local/local-exec
    :sudo (:sudo state/shell-context))
   (is (not (.exists (clojure.java.io/file "/tmp/scp-dest/test.txt"))))
   (teardown)))


(deftest sudo-to-root-scp-from-user-level-transport-ssh
  (transport/ssh "localhost"
                 (sudo/sudo-user {:password sudo-password
                                  :username username}
                                 (setup2)
                                 (is (thrown? clojure.lang.ExceptionInfo #"Pipe closed"
                                      (scp/scp-from
                                       state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                                       :exec :ssh
                                       :exec-fn ssh/ssh-exec
                                       :sudo (:sudo state/shell-context))))
                                 (teardown))))

#_(deftest sudo-to-root-scp-user-level-transport-local
  (transport/local
   (sudo/sudo-user {:password sudo-password
                    :username username}
                   (setup2)
                   (scp/scp-from
                    state/connection ["/tmp/test.txt"] "/tmp/scp-dest"
                    :exec :local
                    :exec-fn local/local-exec
                    :sudo (:sudo state/shell-context))
                   (is (not (.exists (clojure.java.io/file "/tmp/scp-dest/test.txt"))))
                   (teardown))))

(teardown)
