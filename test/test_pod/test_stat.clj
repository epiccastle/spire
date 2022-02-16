(ns test-pod.test-stat
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.stat :as stat]
            [pod.epiccastle.spire.module.rm :as rm]
            [pod.epiccastle.spire.module.upload :as upload]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest test-stat
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (let [{:keys [result stat]} (stat/stat "/etc/profile")]
       (is (= :ok result))
       (->> stat
            keys
            (into #{})
            (= #{:group :gen :uid :atime :mode :inode :btime :size :gid :device-minor :ctime :nlink :file-type :blocks :device :blksize :flags :device-major :user :rdev :mtime})
            is)))))

(deftest test-result-test-funcs
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}
     (rm/rm "/tmp/test-stat")
     (upload/upload {:content "test"
                     :dest "/tmp/test-stat"
                     :mode 0777})
     (let [result (stat/stat "/tmp/test-stat")]
       (is (= :ok (:result result)))
       (is (stat/other-exec? result))
       (is (stat/other-write? result))
       (is (stat/other-read? result))

       (is (stat/group-exec? result))
       (is (stat/group-write? result))
       (is (stat/group-read? result))

       (is (stat/user-exec? result))
       (is (stat/user-write? result))
       (is (stat/user-read? result))

       (is (= (stat/mode-flags result)
              {:user-exec? true
               :group-write? true
               :other-write? true
               :user-read? true
               :other-exec? true
               :group-read? true
               :user-write? true
               :other-read? true
               :group-exec? true}))

       (is (stat/exec? result))
       (is (stat/writeable? result))
       (is (stat/readable? result))

       (is (not (stat/directory? result)))
       (is (not (stat/block-device? result)))
       (is (not (stat/char-device? result)))
       (is (not (stat/symlink? result)))
       (is (not (stat/fifo? result)))
       (is (stat/regular-file? result))
       (is (not (stat/socket? result))))

     (upload/upload {:content "test"
                     :dest "/tmp/test-stat"
                     :mode 0000})
     (let [result (stat/stat "/tmp/test-stat")]
       (is (= :ok (:result result)))
       (is (not (stat/other-exec? result)))
       (is (not (stat/other-write? result)))
       (is (not (stat/other-read? result)))

       (is (not (stat/group-exec? result)))
       (is (not (stat/group-write? result)))
       (is (not (stat/group-read? result)))

       (is (not (stat/user-exec? result)))
       (is (not (stat/user-write? result)))
       (is (not (stat/user-read? result)))

       (is (= (stat/mode-flags result)
              {:user-exec? false
               :group-write? false
               :other-write? false
               :user-read? false
               :other-exec? false
               :group-read? false
               :user-write? false
               :other-read? false
               :group-exec? false}
              ))

       (is (not (stat/exec? result)))
       (is (not (stat/writeable? result)))
       (is (not (stat/readable? result)))))))
