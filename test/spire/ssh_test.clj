(ns spire.ssh-test
  (:require [clojure.test :refer :all]
            [spire.transport :as transport]
            [spire.test-config :as test-config]))

(deftest ssh-host-descriptions
  (testing "ssh host descriptions"
    (transport/ssh {:strict-host-key-checking false
                    :username "root"
                    :hostname "localhost"
                    :port test-config/ssh-port})
    (transport/ssh {:strict-host-key-checking false
                    :hostname "localhost"
                    :port test-config/ssh-port})
    (transport/ssh {:strict-host-key-checking false
                    :host-string (str "root@localhost:" test-config/ssh-port)
                    :port test-config/ssh-port})))
