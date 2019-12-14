(ns spire.module.download-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [spire.module.download :refer :all]
            [spire.module.attrs :refer :all]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.state :as state]
            [spire.config :as config]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]))

(clojure.lang.RT/loadLibrary "spire")

(defn no-scp [& args]
  (assert false "second copy should be skipped"))

(deftest download-test
  (testing "download test"
    (transport/ssh
     "localhost"
     ;; copy
     (is (= true
            (download {:src ".xmonad" :dest "/tmp/bashrc" :recurse true})))
     )))
