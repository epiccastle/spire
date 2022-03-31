(ns test-pod.test-apt
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.apt :as apt]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))


(deftest apt
  (when conf/sudo?
    (binding [state/output-module :silent]
      (transport/ssh
       {:hostname conf/hostname
        :username conf/username}
       (is (thrown? clojure.lang.ExceptionInfo #"module failed"
                    (apt/apt :update)))

       (sudo/sudo-user
        {:password conf/sudo-password}
        (let [{:keys [exit out err update]}
              (apt/apt :update {})]
          (is (zero? exit))
          (is (every? :method update)))

        (let [{:keys [exit out err packages] :as result}
              (apt/apt :remove ["traceroute"])]
          (is (zero? exit))
          (is (= (into #{} (keys packages)) #{:upgraded :installed :removed})))

        (let [{:keys [exit out err packages] :as result}
              (apt/apt :install ["traceroute"])]
          (is (zero? exit))
          (is (= (into #{} (keys packages)) #{:upgraded :installed :removed})))
        ))

      (transport/local
       (is (thrown? clojure.lang.ExceptionInfo #"module failed"
                    (apt/apt :update)))

       (sudo/sudo-user
        {:password conf/sudo-password}
        (let [{:keys [exit out err update]}
              (apt/apt :update {})]
          (is (zero? exit))
          (is (every? :method update)))

        (let [{:keys [exit out err packages] :as result}
              (apt/apt :remove ["traceroute"])]
          (is (zero? exit))
          (is (= (into #{} (keys packages)) #{:upgraded :installed :removed})))

        (let [{:keys [exit out err packages] :as result}
              (apt/apt :install ["traceroute"])]
          (is (zero? exit))
          (is (= (into #{} (keys packages)) #{:upgraded :installed :removed})))
        )))))
