(ns test-pod.test-facts
  (:require [test-pod.conf :refer [username hostname]]
            [clojure.string :as string]
            [clojure.test :refer [is deftest]]

            [pod.epiccastle.spire.facts :as facts]
            [pod.epiccastle.spire.transport :as transport]))

(deftest make-which
  (is (string/starts-with? (facts/make-which :bash) "echo "))
  (is (string/starts-with? (facts/make-which :csh) "echo "))
  (is (string/starts-with? (facts/make-which :fish) "echo ")))

(deftest process-uname
  (is (= (facts/process-uname "Linux vash 4.15.0-142-generic #146-Ubuntu SMP Tue Apr 13 01:11:19 UTC 2021 x86_64 x86_64 x86_64 GNU/Linux")
         {:kernel {:name "Linux" :release "4.15.0-142-generic" :version "#146-Ubuntu"}
          :machine "SMP"
          :processor "Tue"
          :platform "Apr"
          :os "13"
          :node "vash"
          :string "Linux vash 4.15.0-142-generic #146-Ubuntu SMP Tue Apr 13 01:11:19 UTC 2021 x86_64 x86_64 x86_64 GNU/Linux"})))

(deftest guess-mac-codename
  (is (= :yosemite (facts/guess-mac-codename "10.10"))))

(deftest fetch-facts
  (let [facts (facts/fetch-facts)]
    (is (= (into #{} (keys facts))
           #{:system :paths :uname :ssh-config :shell :user}))

    (is (= (:ssh-config facts)
           {:key "local"}))))

(deftest get-fact
  (transport/ssh {:username username :hostname hostname}
                 (let [ssh-config (facts/get-fact [:ssh-config])]
                   (is (= username (:username ssh-config)))
                   (is (= hostname (:hostname ssh-config))))))
