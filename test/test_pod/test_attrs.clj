(ns test-pod.test-attrs
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.attrs :as attrs]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest attrs
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (sudo/sudo-user
      {:password conf/sudo-password}

      (is (thrown? clojure.lang.ExceptionInfo #"module failed"
                   (attrs/attrs {:path "/var/www/non-existent-file"
                                 :owner "www-data"
                                 :group "www-data"})))

      (utils/bash "touch /tmp/foo")

      (sudo/sudo-user
       {:password conf/sudo-password}
       (let [{:keys [exit out err] :as result}
             (attrs/attrs {:path "/tmp/foo"
                           :owner "www-data"
                           :group "www-data"})]
         (is (zero? exit))
         (is (= "www-data\n" (utils/bash "stat -c%U /tmp/foo")))
         (is (= "www-data\n" (utils/bash "stat -c%G /tmp/foo")))))))))
