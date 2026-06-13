(ns test-pod.test-curl
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.curl :as curl]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest curl
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     #_ (sudo/sudo-user
      {:password conf/sudo-password})

     (let [{:keys [exit out err out-lines] :as result} (curl/curl {:url "https://epiccastle.io"})]
       (is (= #{:status :headers :decoded :body :exit :result} (into #{} (keys result))))
       (is (zero? exit)))

     (let [{:keys [exit out err decoded]}
           (curl/curl
            {:url "https://tools.learningcontainer.com/sample-json.json"
             :decode-opts {:key-fn keyword}})]
       (is (zero? exit))
       (is (get decoded :address))
       (is (get decoded :phoneNumbers))
       )

      )))
