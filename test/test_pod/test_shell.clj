(ns test-pod.test-shell
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.shell :as shell]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest sudo-shell
  (binding [state/output-module :quiet]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (sudo/sudo-user
      {:password conf/sudo-password}
      (let [{:keys [exit out]} (shell/shell {:cmd "whoami; exit 3" :print true})]
        (is (= exit 3))
        (is (= out "root\n")))))

    (transport/local
     (sudo/sudo-user
      {:password conf/sudo-password}
      (let [{:keys [out exit]} (shell/shell {:cmd "whoami; exit 3" :print true})]
        (is (= exit 3))
        (is (= out "root\n")))))))

(deftest shell
  (binding [state/output-module :quiet]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (sudo/sudo-user
      {:password conf/sudo-password}
      (let [{:keys [exit out]} (shell/shell {:cmd "whoami; exit 3" :print true})]
        (is (= exit 3))
        (is (= out "root\n")))))

    (transport/local
     (sudo/sudo-user
      {:password conf/sudo-password}
      (let [{:keys [out exit]} (shell/shell {:cmd "whoami; exit 3" :print true})]
        (is (= exit 3))
        (is (= out "root\n"))))))
  )
