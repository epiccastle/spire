(ns spire.module.download-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [spire.module.download :refer :all]
            [spire.module.attrs :refer :all]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.state :as state]
            [spire.config :as config]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]
            [spire.test-config :as test-config]))

(clojure.lang.RT/loadLibrary "spire")

(defn no-scp [& args]
  (assert false "second copy should be skipped"))

(defn pwd []
  (let [{:keys [exit err out]} (shell/sh "pwd")]
    (assert (zero? exit))
    (string/trim out)))

(deftest download-test
  (testing "download test"
    (let [test-dir (str (io/file (pwd) "test/files"))]
      (test-utils/with-temp-file-names [tf tf2 tf3]
        (test-utils/makedirs tf)
        (transport/ssh
         test-config/localhost
         ;; copy directory recursively
         (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                (download {:src test-dir :dest tf :recurse true})))
         (is (= (test-utils/run
                  (format "cd \"%s/localhost/files\" && find . -exec %s {} \\;"
                          tf (test-utils/make-stat-command ["%s" "%F" "%n"])))
                (test-utils/ssh-run
                 (format "cd \"%s\" && find . -exec %s {} \\;"
                         test-dir (test-utils/make-stat-command ["%s" "%F" "%n"])))))
         (is (= (test-utils/run
                  (format "cd \"%s/localhost/files\" && find . -type f -exec %s {} \\;"
                          tf
                          (test-utils/make-md5-command)))
                (test-utils/ssh-run
                 (format "cd \"%s\" && find . -type f -exec %s {} \\;"
                         test-dir
                         (test-utils/make-md5-command)))))

         (with-redefs [spire.scp/scp-from no-scp]
           ;; reset attrs with hard coded mode and dir-mode
           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download {:src test-dir :dest tf :recurse true :dir-mode 0777 :mode 0666})))
           (is (= (test-utils/run
                    (format "cd \"%s/localhost/files\" && find . -type f -exec %s {} \\;"
                            tf
                            (test-utils/make-stat-command ["%s" "%a" "%F" "%n"])))
                  (test-utils/ssh-run
                   (format "cd \"%s\" && find . -type f -exec %s {} \\;"
                           test-dir
                           (test-utils/make-stat-command ["%s" "666" "%F" "%n"])))))
           (is (= (test-utils/run
                    (format "cd \"%s/localhost/files\" && find . -type d -exec %s {} \\;"
                            tf
                            (test-utils/make-stat-command ["%s" "%a" "%F" "%n"])))
                  (test-utils/ssh-run
                   (format "cd \"%s\" && find . -type d -exec %s {} \\;"
                           test-dir
                           (test-utils/make-stat-command ["%s" "777" "%F" "%n"])))))

           ;; reset attrs with :preserve
           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download {:src test-dir :dest tf :recurse true :preserve true})))
           (is (= (test-utils/run
                    (format "cd \"%s/localhost/files\" && find . -exec %s {} \\;"
                            tf
                            (test-utils/make-stat-command ["%s" "%a" ;;"%Y" "%X"
                                                           "%F" "%n"])))
                  (test-utils/ssh-run
                   (format "cd \"%s\" && find . -exec %s {} \\;"
                           test-dir
                           (test-utils/make-stat-command ["%s" "%a" ;;"%Y" "%X"
                                                          "%F" "%n"]))))))

         ;; copy with preserve to begin with
         (test-utils/makedirs tf2)
         (is (or
              (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                 (download {:src test-dir :dest tf2 :recurse true :preserve true}))
              ;; macos has both changed sometimes. othertimes not.
              (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :changed}}
                 (download {:src test-dir :dest tf2 :recurse true :preserve true}))))
         (is (= (test-utils/run
                  (format "cd \"%s/localhost/files\" && find . -exec %s {} \\;"
                          tf2
                          (test-utils/make-stat-command ["%s" "%a" ;;"%Y" "%X"
                                                         "%F" "%n"])))
                (test-utils/ssh-run
                 (format "cd \"%s\" && find . -exec %s {} \\;"
                         test-dir
                         (test-utils/make-stat-command ["%s" "%a" ;;"%Y" "%X"
                                                        "%F" "%n"])))))
         (is (= (test-utils/run (format "cd \"%s/localhost/files\" && find . -type f -exec md5sum {} \\;" tf2))
                (test-utils/ssh-run (format "cd \"%s\" && find . -type f -exec md5sum {} \\;" test-dir))))

         ;; download single file into a directory
         (test-utils/makedirs tf3)
         (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                (download {:src (io/file test-dir "copy/test.txt") :dest tf3})))
         (is (= (slurp (io/file test-dir "copy/test.txt"))
                (slurp (io/file tf3 "localhost/test.txt"))))

         (with-redefs [spire.scp/scp-from no-scp]
           (is (= {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}
                  (download {:src (io/file test-dir "copy/test.txt") :dest tf3})))
           (is (= (slurp (io/file test-dir "copy/test.txt"))
                  (slurp (io/file tf3 "localhost/test.txt"))))

           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download {:src (io/file test-dir "copy/test.txt") :dest tf3 :mode 0666})))
           (is (= (string/trim (test-utils/run (format "%s \"%s/localhost/test.txt\""
                                                       (test-utils/make-stat-command ["%s" "%a"])
                                                       tf3)))
                  "43 666"))))))))
