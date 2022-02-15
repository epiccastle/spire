(ns test-pod.test-line-in-file
  (:require [test-pod.conf :as conf]
            [test-pod.utils :as utils]
            [clojure.test :refer [is deftest]]
            [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.module.upload :as upload]
            [pod.epiccastle.spire.module.line-in-file :as line-in-file]
            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            ))

(deftest line-in-file
  (binding [state/output-module :silent]
    (transport/ssh
     {:hostname conf/hostname
      :username conf/username}

     (upload/upload {:src "../files/line-in-file/simple-file.txt"
                     :dest "/tmp/simple-file.txt"})
     (is (= (line-in-file/line-in-file :get {:path "/tmp/simple-file.txt"
                                             :line-num 7})
            {:exit 0
             :result :ok
             :line-num 7
             :line "This is line #7"
             :line-nums [7]
             :lines ["This is line #7"]
             :matches {7 "This is line #7"}}))

     (is (= (line-in-file/line-in-file :get {:path "/tmp/simple-file.txt"
                                             :regexp #"line #1"
                                             :match :all})

            {:exit 0
             :result :ok
             :line-num 10
             :line "This is line #10"
             :line-nums [1 10]
             :lines ["This is line #1" "This is line #10"]
             :matches {1 "This is line #1" 10 "This is line #10"}}
            ))

     (upload/upload {:src "../files/line-in-file/regexp-file.txt"
                     :dest "/tmp/regexp-file.txt"})
     (-> (line-in-file/line-in-file :get {:path "/tmp/regexp-file.txt"
                                          :regexp #"and it contains a"
                                          :match :all})
         (select-keys [:exit :result :line-nums])
         (= {:exit 0 :result :ok :line-nums [2 4 5 9 12 13 15 18 19]})
         is)

     (is (= (line-in-file/line-in-file :present {:path "/tmp/regexp-file.txt"
                                                 :regexp #"and it contains a"
                                                 :line "line to insert and it contains a"})

            {:exit 0
             :out ""
             :err ""
             :result :changed}))

     (is (= :changed (:result (line-in-file/line-in-file :absent {:path "/tmp/regexp-file.txt"
                                                                  :regexp #"and it contains a"
                                                                  :line "line to insert and it contains a"}))))

     (-> (line-in-file/line-in-file :get {:path "/tmp/regexp-file.txt"
                                           :regexp #"and it contains a"
                                          :match :all})
         :line-nums
         (= [3 4 8 11 12 14 17 18])
         is))))
