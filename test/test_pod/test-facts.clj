(ns test-pod.test-facts
  (:require [babashka.pods :as pods]
            [clojure.string :as string]
            ))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.facts :as facts])

(assert (string/starts-with? (facts/make-which :bash) "echo "))
(assert (string/starts-with? (facts/make-which :csh) "echo "))
(assert (string/starts-with? (facts/make-which :fish) "echo "))

(assert (= (facts/process-uname "Linux vash 4.15.0-142-generic #146-Ubuntu SMP Tue Apr 13 01:11:19 UTC 2021 x86_64 x86_64 x86_64 GNU/Linux")
           {:kernel {:name "Linux" :release "4.15.0-142-generic" :version "#146-Ubuntu"}
            :machine "SMP"
            :processor "Tue"
            :platform "Apr"
            :os "13"
            :node "vash"
            :string "Linux vash 4.15.0-142-generic #146-Ubuntu SMP Tue Apr 13 01:11:19 UTC 2021 x86_64 x86_64 x86_64 GNU/Linux"}))

(assert (= :yosemite (facts/guess-mac-codename "10.10")))

(let [facts (facts/fetch-facts)]
  (assert (= (keys facts)
             [:system :paths :uname :ssh-config :shell :user]))

  (assert (= (:ssh-config facts)
             {:key "local"})))

(def username (or (System/getenv "TEST_USER") (System/getenv "USER")))
(def hostname (or (System/getenv "TEST_HOST") "localhost"))

(require '[pod.epiccastle.spire.transport :as transport])

(transport/ssh {:username username :hostname hostname}
               (let [ssh-config (facts/get-fact [:ssh-config])]
                 (assert (= username (:username ssh-config)))
                 (assert (= hostname (:hostname ssh-config)))))
