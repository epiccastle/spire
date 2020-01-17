(ns spire.test-config)

(def localhost
  {:hostname "localhost"
   :port (-> (System/getenv "SSH_TEST_PORT") (or "22") Integer/parseInt)
   :strict-host-key-checking "no"
   :key :localhost})

(def localhost-root
  {:hostname "localhost"
   :username "root"
   :port (-> (System/getenv "SSH_TEST_PORT") (or "22") Integer/parseInt)
   :strict-host-key-checking "no"
   :key :localhost-root})
