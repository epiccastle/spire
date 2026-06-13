(ns spire-test.remote-test
  (:require [clojure.test :refer :all]
            [spire.remote :refer :all]
            [spire.transport :as transport]
            #_[spire.test-utils :as test-utils]
            #_[spire.ssh :as ssh]
            [spire.state :as state]
            [clojure.string :as string]
            [clojure.java.io :as io]
            ))

(deftest a-test
  (testing "test"
    (is (= 1 1))))
