(ns test-pod.test-sysctl
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sysctl :as sysctl]
            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.upload :as upload]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest test-sysctl
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}

     (sudo/sudo-user
      {:password conf/sudo-password}
      (sysctl/sysctl :present {:name "fs.protected_symlinks"
                               :value "1"})
      (is (= :ok
             (:result (sysctl/sysctl :present
                                     {:name "fs.protected_symlinks"
                                      :value "1"}))))
      (is (= :changed
             (:result (sysctl/sysctl :present
                                     {:name "fs.protected_symlinks"
                                      :value "0"}))))
      (is (= :ok
             (:result (sysctl/sysctl :present
                                     {:name "fs.protected_symlinks"
                                      :value "0"}))))))))
