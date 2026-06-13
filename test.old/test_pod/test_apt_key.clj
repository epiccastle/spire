(ns test-pod.test-apt-key
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.sudo :as sudo]
            [pod.epiccastle.spire.module.apt-key :as apt-key]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))


(deftest apt-key
  (when conf/sudo?
    (binding [state/output-module :silent]
      (transport/ssh
       {:hostname conf/hostname
        :username conf/username}
       (sudo/sudo-user
        {:password conf/sudo-password}

        (try
          (apt-key/apt-key
           :absent {:public-key-url "https://deb.goaccess.io/gnugpg.key"
                    :keyring "/etc/apt/trusted.gpg.d/goaccess.gpg"
                    :fingerprint "C03B 4888 7D5E 56B0 4671 5D32 97BD 1A01 3344 9C3D"})
          (catch clojure.lang.ExceptionInfo _)))

       (sudo/sudo-user
        {:password conf/sudo-password}

        (let [{:keys [exit out err] :as result}
              (apt-key/apt-key :absent {:fingerprint "C03B 4888 7D5E 56B0 4671 5D32 97BD 1A01 3344 9C3D"})
              ]
          ;;      (prn result)
          (is (zero? exit)))

        (let [{:keys [exit out err] :as result}
              (apt-key/apt-key :present {:public-key-url "https://deb.goaccess.io/gnugpg.key"
                                         :keyring "/etc/apt/trusted.gpg.d/goaccess.gpg"
                                         :fingerprint "C03B 4888 7D5E 56B0 4671 5D32 97BD 1A01 3344 9C3D"})
              ]
          ;;        (prn result)
          (is (= 255 exit))))

       (transport/local
        (sudo/sudo-user
         {:password conf/sudo-password}

         (let [{:keys [exit out err] :as result}
               (apt-key/apt-key :present {:public-key-url "https://deb.goaccess.io/gnugpg.key"
                                          :keyring "/etc/apt/trusted.gpg.d/goaccess.gpg"
                                          :fingerprint "C03B 4888 7D5E 56B0 4671 5D32 97BD 1A01 3344 9C3D"})
               ]
           ;;      (prn result)
           (is (zero? exit)))

         (let [{:keys [exit out err] :as result}
               (apt-key/apt-key :absent {:fingerprint "C03B 4888 7D5E 56B0 4671 5D32 97BD 1A01 3344 9C3D"})
               ]
           ;;        (prn result)
           (is (= 255 exit)))


         ))
       )

      )))
