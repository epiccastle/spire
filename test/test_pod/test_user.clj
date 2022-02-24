(ns test-pod.test-user
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.user :as user]
            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.upload :as upload]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(when conf/sudo?
  (deftest test-user
    (binding [state/output-module :silent]
      (transport/ssh
       {:hostname conf/hostname
        :username conf/username}

       (sudo/sudo-user
        {:password conf/sudo-password}
        (is (= :ok (:result (user/user :absent {:name "088527a000c03b0057d5f77757c59dbd"}))))
        (is (= :ok (:result (user/user :present {:name conf/username}))))
        )))))
