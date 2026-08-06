(ns spire-test.remote-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [spire.remote :refer :all]
            [spire.transport :as transport]
            [spire.module.upload :as upload]
            [spire.module.download :as download]
            [spire.facts :as facts]
            [spire.state :as state]
            [clojure.pprint :as pprint]
            [spire-test.config :as config]
            [spire-test.facts-spec :as facts-spec]))

(deftest ssh-transport
  (doseq [host (config/select-hosts {})]
    (let [{:keys [port username]} (config/host-ports host)]
      (testing (str "facts for " host)
        (transport/ssh {:username (or username "root")
                        :port port
                        :hostname "localhost"
                        :password "root-access-please"
                        :strict-host-key-checking false}
                       (let [facts (@facts/state (str (or username "root") "@localhost:" port))]
                         (is (s/valid? ::facts-spec/system facts)
                             (s/explain-str ::facts-spec/system facts)))))))
  )

(deftest ssh-2
  (doseq [host (config/select-hosts {:only #{:ubuntu}})]
    (let [{:keys [port username]} (config/host-ports host)]
      (testing (str "upload and download for " host)
        (transport/ssh
          {:username (or username "root")
           :port port
           :hostname "localhost"
           :password "root-access-please"
           :strict-host-key-checking false}
          (prn (upload/upload {:content "sample data"
                               :dest "/tmp/sample.txt"}))

          ))))

  )
