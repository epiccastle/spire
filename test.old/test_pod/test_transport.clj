(ns test-pod.test-transport
  (:require [test-pod.conf :refer [username hostname]]
            [test-pod.utils :refer [connections]]
            [clojure.test :refer [is deftest]]

            [pod.epiccastle.spire.transport :as transport]))

(deftest test-connection
  (let [search (str hostname ":ssh")
        conns (count (connections search))
        session (transport/connect {:username username :hostname hostname :port 22})]
    (is (= 1 (- (count (connections search)) conns)))
    (transport/disconnect session)
    (is (= 0 (- (count (connections search)) conns)))))

;; test nested connections dont return unserialisable EDN data
(deftest nested-connections
  (is (transport/ssh "localhost" (transport/ssh "localhost" true))))
