(ns test-pod.test-authorized-keys
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.authorized-keys :as authorized-keys]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest authorized-keys
  (when conf/sudo?
    (binding [state/output-module :silent]
      (transport/ssh
       {:hostname conf/hostname
        :username conf/username}
       (sudo/sudo-user
        {:password conf/sudo-password}

        (let [{:keys [exit out err out-lines result]} (authorized-keys/authorized-keys :get {:user conf/username})]
          (is (zero? exit))
          (is (= :ok result))))))))
