(ns spire.module.upload-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [spire.module.upload :refer :all]
            [spire.transport :as transport]
            [spire.config :as config]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]))

(clojure.lang.RT/loadLibrary "spire")

(deftest upload-test
  (testing "upload test"
    (transport/ssh
     "localhost"
     (is (= nil
            (upload {:src "project.clj"
                     :dest "/tmp/project.clj"}))))))
