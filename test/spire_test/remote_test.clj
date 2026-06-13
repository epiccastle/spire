(ns spire-test.remote-test
  (:require [clojure.test :refer :all]
            [spire.remote :refer :all]
            [spire.transport :as transport]
            [spire.facts :as facts]
            [spire.state :as state]
            [clojure.pprint :as pprint]
            [spire-test.docker :as docker]))

(deftest ssh-transport
  (docker/cleanup)
  (docker/build {:root-password "root-access-please"})
  (docker/start {:ssh-port 9876})

  (transport/ssh {:username "root"
                  :port 9876
                  :hostname "localhost"
                  :password "root-access-please"
                  :strict-host-key-checking false}
                 (println "Yes")
                 (prn '*connection* state/*connection*)
                 (prn '*host-config* state/*host-config*)
                 (prn '*shell-context* state/*shell-context*)
                 (prn 'facts)
                 (clojure.pprint/pprint @facts/state)
                 )

  (docker/cleanup)
  )
