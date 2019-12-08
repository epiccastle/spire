(ns spire.module.copy-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [spire.module.copy :refer :all]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]))

(deftest copy-test
  (testing "copy test"
    (with-redefs [spire.transport/pipelines test-utils/test-pipelines
                  spire.ssh/ssh-exec test-utils/test-ssh-exec
                  spire.scp/scp-to test-utils/test-scp-to
                  spire.output/print-form identity]
      (test-utils/with-temp-file-names [tmp]
        (is (= (copy {:src "test/files/copy/test.txt" :dest tmp})
               {:result :changed}))
        (is (= (slurp "test/files/copy/test.txt")
               (slurp tmp))))

      (test-utils/with-temp-file-names [tmp]
        (is (= (copy {:src "test/files/copy/test.txt" :dest tmp :mode 0777})
               {:result :changed :exit 0 :out "" :err ""}))
        (is (= "777" (-> (shell/sh "stat" "-c" "%a" tmp) :out string/trim)))
        ;; second and later copys should not actually copy file contents
        (with-redefs [spire.scp/scp-to (fn [& args] (assert false "second copy should be skipped"))]
          (is (= (copy {:src "test/files/copy/test.txt" :dest tmp :mode 0777})
                 {:result :ok}))
          (is (= (copy {:src "test/files/copy/test.txt" :dest tmp :mode "go-w"})
                 {:result :changed :exit 0 :out "" :err ""}))
          (is (= "755" (-> (shell/sh "stat" "-c" "%a" tmp) :out string/trim))))
        )

      (when (test-utils/is-root?)
        (let [{:keys [username uid]} (test-utils/last-user)
              {:keys [groupname gid]} (test-utils/last-group)]
          (test-utils/with-temp-file-names [tmp]
            (is (= (copy {:src "test/files/copy/test.txt" :dest tmp :mode 0777 :owner "root" :group "root"})
                   {:result :changed :exit 0 :out "" :err ""}))
            (is (= "777" (-> (shell/sh "stat" "-c" "%a" tmp) :out string/trim)))
            (is (= "0" (-> (shell/sh "stat" "-c" "%u" tmp) :out string/trim)))
            (is (= "0" (-> (shell/sh "stat" "-c" "%g" tmp) :out string/trim)))
            ;; second and later copys should not actually copy file contents
            (with-redefs [spire.scp/scp-to (fn [& args] (assert false "second copy should be skipped"))]
              (is (= (copy {:src "test/files/copy/test.txt" :dest tmp :mode 0777  :owner "root" :group "root"})
                     {:result :ok}))
              (is (= (copy {:src "test/files/copy/test.txt" :dest tmp :owner username :group groupname})
                     {:result :changed :exit 0 :out "" :err ""}))
              (is (= "777" (-> (shell/sh "stat" "-c" "%a" tmp) :out string/trim)))
              (is (= uid (-> (shell/sh "stat" "-c" "%u" tmp) :out string/trim)))
              (is (= gid (-> (shell/sh "stat" "-c" "%g" tmp) :out string/trim))))))))))
