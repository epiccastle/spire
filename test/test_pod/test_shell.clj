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
  (when conf/sudo?
    (binding [state/output-module :silent]
      (transport/ssh
       {:hostname conf/hostname
        :username conf/username}
       (sudo/sudo-user
        {:password conf/sudo-password}
        (let [{:keys [exit out err]} (shell/shell {:cmd "whoami; echo a 1>&2; exit 3"
                                                   :print true
                                                   :ok-exit (fn [code] (= code 3))})]
          (is (= exit 3))
          (is (= out "root\n"))
          (is (= err (str "[sudo] password for " conf/username ": a\n")))
          )))

      (transport/local
       (sudo/sudo-user
        {:password conf/sudo-password}
        (let [{:keys [out exit err]} (shell/shell {:cmd "whoami; echo a 1>&2; exit 3"
                                                   :print true
                                                   :ok-exit (fn [code] (= code 3))})]
          (is (= exit 3))
          (is (= out "root\n"))
          (is (= err "a\n"))
          ))))))

(deftest shell
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (let [{:keys [exit out err]} (shell/shell {:cmd "whoami; echo a 1>&2; exit 3"
                                                :print true
                                                :ok-exit (fn [code] (= code 3))})]
       (is (= exit 3))
       (is (= out conf/username-n))
       (is (= err "a\n")))

     (let [{:keys [exit out err]} (shell/shell {:cmd "echo start; cat; echo end; exit 0"
                                                :stdin "middle\n"})]
       (is (= exit 0))
       (is (= out "start\nmiddle\nend\n")))

     (let [{:keys [exit out err]} (shell/shell {:cmd "echo start; cat; echo end; exit 3"
                                                :stdin "middle\n"
                                                :ok-exit (fn [code] (= code 3))})]
       (is (= exit 3))
       (is (= out "start\nmiddle\nend\n")))
     )

    (transport/local
     (let [{:keys [out exit err]} (shell/shell {:cmd "whoami; echo a 1>&2; exit 3"
                                                :print true
                                                :ok-exit (fn [code] (= code 3))})]
       (is (= exit 3))
       (is (= out conf/username-n))
       (is (= err "a\n")))

     (let [{:keys [exit out err]} (shell/shell {:cmd "echo start; cat; echo end; exit 0"
                                                :stdin "middle\n"})]
       (is (= exit 0))
       (is (= out "start\nmiddle\nend\n")))

     (let [{:keys [exit out err]} (shell/shell {:cmd "echo start; cat; echo end; exit 3"
                                                :stdin "middle\n"
                                                :ok-exit (fn [code] (= code 3))})]
       (is (= exit 3))
       (is (= out "start\nmiddle\nend\n")))))
  )
