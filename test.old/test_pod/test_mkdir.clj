(ns test-pod.test-mkdir
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.upload :as upload]
            [pod.epiccastle.spire.module.shell :as shell]
            [pod.epiccastle.spire.module.mkdir :as mkdir]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest mkdir
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}

     (shell/shell {:cmd "rm -rf /tmp/test-mkdir/foo"})
     (is (= :changed (:result (mkdir/mkdir {:path "/tmp/test-mkdir/foo"}))))

     (is (= ["foo"]
            (:out-lines
             (shell/shell {:cmd "ls /tmp/test-mkdir"})))))))
