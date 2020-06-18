(ns spire.remote-test
  (:require [clojure.test :refer :all]
            [spire.remote :refer :all]
            [spire.transport :as transport]
            [spire.test-utils :as test-utils]
            [spire.ssh :as ssh]
            [spire.state :as state]
            [clojure.string :as string]
            [clojure.java.io :as io]
            ))

(clojure.lang.RT/loadLibrary "spire")

(deftest a-test
  (testing "path-full-info"
    (test-utils/with-temp-file-names [tf]
      (spit tf "This is a test file")
      (test-utils/run (format "chmod 0644 '%s'" tf))
      (transport/ssh
       {:hostname "localhost"
        :port (-> (System/getenv "SSH_TEST_PORT") (or "22") Integer/parseInt)
        :strict-host-key-checking "no"
        :key :localhost}
       (let [run (fn [command]
                   (let [{:keys [out exit err]}
                         (ssh/ssh-exec (state/get-connection) command "" "UTF-8" {})]
                     (comment
                       (println "command:" command)
                       (println "exit:" exit)
                       (println "out:" out)
                       (println "err:" err))
                     (if (zero? exit)
                       (string/trim out)
                       "")))]
         (let [info (path-full-info run tf)]
           (is (= [""] (keys info)))
           (let [file-info (info "")
                 choice (select-keys file-info [:type :filename :md5sum :mode-string :mode :size])
                 ]
             (is (= choice
                    {:type :file
                     :filename (.getName (io/file tf))
                     :md5sum "0b26e313ed4a7ca6904b0e9369e5b957"
                     :mode-string "644"
                     :mode 420
                     :size 19})))))))))
