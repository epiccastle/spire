(ns spire.test-config)

(def ssh-port (-> (System/getenv "SSH_TEST_PORT") (or "22") Integer/parseInt))

(def localhost
  {:hostname "localhost"
   :port ssh-port
   :strict-host-key-checking "no"
   :key :localhost})

(def localhost-root
  {:hostname "localhost"
   :username "root"
   :port ssh-port
   :strict-host-key-checking "no"
   :key :localhost-root})
