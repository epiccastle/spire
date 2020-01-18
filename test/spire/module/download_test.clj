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
         (is (test-utils/recurse-file-size-type-name-match? (str tf "/localhost/files") test-dir))
         (is (test-utils/find-files-md5-match? (str tf "/localhost/files") test-dir))

         (with-redefs [spire.scp/scp-from no-scp]
           ;; reset attrs with hard coded mode and dir-mode
           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download {:src test-dir :dest tf :recurse true :dir-mode 0777 :mode 0666})))
           (is (test-utils/recurse-file-size-type-name-match? (str tf "/localhost/files") test-dir))
           (is (test-utils/find-local-files-mode-is? (str tf "/localhost/files") "666"))
           (is (test-utils/find-local-dirs-mode-is? (str tf "/localhost/files") "777"))

           ;; reset attrs with :preserve
           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download {:src test-dir :dest tf :recurse true :preserve true})))
           (is (test-utils/recurse-file-size-type-name-mode-match? (str tf "/localhost/files") test-dir))
           #_ (is (test-utils/recurse-access-modified-match? (str tf "/localhost/files") test-dir)))

         ;; copy with preserve to begin with
         (test-utils/makedirs tf2)
         (let [result (download {:src test-dir :dest tf2 :recurse true :preserve true})]
           (is (or
                (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                   result)
                ;; macos has both changed sometimes. othertimes not???
                (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :changed}}
                   result))))

         (is (test-utils/recurse-file-size-type-name-mode-match? (str tf2 "/localhost/files") test-dir))
         #_ (is (test-utils/recurse-access-modified-match? (str tf2 "/localhost/files") test-dir))
         (is (test-utils/find-files-md5-match? (str tf2 "/localhost/files") test-dir))

         ;; download single file into a directory
         (test-utils/makedirs tf3)
         (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                (download {:src (io/file test-dir "copy/test.txt") :dest tf3})))
         (is (test-utils/files-md5-match? (str tf3 "/localhost/test.txt") (str test-dir "/copy/test.txt")))

         (with-redefs [spire.scp/scp-from no-scp]
           (is (= {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}
                  (download {:src (io/file test-dir "copy/test.txt") :dest tf3})))
           (is (test-utils/files-md5-match? (str tf3 "/localhost/test.txt") (str test-dir "/copy/test.txt")))

           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download {:src (io/file test-dir "copy/test.txt") :dest tf3 :mode 0666})))

           (is (= "43 666"
                  (test-utils/stat-local (str tf3 "/localhost/test.txt") ["%s" "%a"])))))))))
