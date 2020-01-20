(ns spire.ssh-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [spire.transport :as transport]
            [spire.module.shell :as shell]
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

(deftest ssh-nesting
  (testing "ssh lexical nesting tests"
    (transport/ssh
     test-config/localhost-root
     (is (= "root"
            (string/trim (:out (shell/shell {:cmd "whoami"})))))

     (transport/ssh
      test-config/localhost-root
      (is (= "root"
             (string/trim (:out (shell/shell {:cmd "whoami"})))))
      )

     (is (= "root"
            (string/trim (:out (shell/shell {:cmd "whoami"})))))))

  (testing "ssh lexical nesting tests"
    (transport/ssh
     test-config/localhost-root
     (is (= "root"
            (string/trim (:out (shell/shell {:cmd "whoami"})))))

     (transport/ssh
      test-config/localhost
      (is (= (System/getProperty "user.name")
             (string/trim (:out (shell/shell {:cmd "whoami"})))))

      (transport/ssh
       test-config/localhost-root
       (is (= "root"
              (string/trim (:out (shell/shell {:cmd "whoami"}))))))

      (is (= (System/getProperty "user.name")
             (string/trim (:out (shell/shell {:cmd "whoami"}))))))

     (is (= "root"
            (string/trim (:out (shell/shell {:cmd "whoami"}))))))))

(deftest ssh-group-nesting
  (testing "ssh-group lexical nesting tests"
    (transport/ssh-group
     [test-config/localhost-root test-config/localhost]
     (is (#{(System/getProperty "user.name") "root"}
          (string/trim (:out (shell/shell {:cmd "whoami"})))))


     (transport/ssh-group
      [test-config/localhost-root test-config/localhost]
      (is (#{(System/getProperty "user.name") "root"}
           (string/trim (:out (shell/shell {:cmd "whoami"}))))))

     (is (#{(System/getProperty "user.name") "root"}
          (string/trim (:out (shell/shell {:cmd "whoami"}))))))))
