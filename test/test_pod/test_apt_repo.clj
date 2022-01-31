(ns test-pod.test-apt-repo
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.apt-repo :as apt-repo]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest apt-key
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (sudo/sudo-user
      {:password conf/sudo-password}

      (apt-repo/apt-repo :absent {:repo "ppa:ondrej/php"})

      (is (= 255 (:exit (apt-repo/apt-repo :present {:repo "ppa:ondrej/php"}))))
      (is (= 0 (:exit (apt-repo/apt-repo :present {:repo "ppa:ondrej/php"}))))
      (is (= 255 (:exit (apt-repo/apt-repo :absent {:repo "ppa:ondrej/php"}))))
      (is (= 0 (:exit (apt-repo/apt-repo :absent {:repo "ppa:ondrej/php"}))))))))
