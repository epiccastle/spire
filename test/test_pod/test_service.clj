(ns test-pod.test-service
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.shell :as shell]
            [pod.epiccastle.spire.module.service :as service]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(when conf/sudo?
  (deftest service
    (binding [state/output-module :silent]
      (transport/ssh
       {:hostname conf/hostname
        :username conf/username}
       (sudo/sudo-user {:password conf/sudo-password}
                       (is (= (:changed (:result (service/service :restarted {:name "cron"}))))))
       ))))
