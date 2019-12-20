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
            [spire.test-utils :as test-utils]))

(clojure.lang.RT/loadLibrary "spire")

(defn no-scp [& args]
  (assert false "second copy should be skipped"))

(defn pwd []
  (let [{:keys [exit err out]} (shell/sh "pwd")]
    (assert (zero? exit))
    (string/trim out)))

#_ (pwd)

(deftest download-test
  (testing "download test"
    (let [test-dir (str (io/file (pwd) "test/files"))]
      (test-utils/with-temp-file-names [tf tf2]
        (test-utils/makedirs tf)
        (transport/ssh
         "localhost"
         ;; copy directory recursively
         (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                (download {:src test-dir :dest tf :recurse true})))
         (is (= (test-utils/run (format "cd \"%s/localhost/files\" && find . -exec stat -c \"%%s %%F %%n\" {} \\;" tf))
                (test-utils/ssh-run (format "cd \"%s\" && find . -exec stat -c \"%%s %%F %%n\" {} \\;" test-dir))))
         (is (= (test-utils/run (format "cd \"%s/localhost/files\" && find . -type f -exec md5sum {} \\;" tf))
                (test-utils/ssh-run (format "cd \"%s\" && find . -type f -exec md5sum {} \\;" test-dir))))

         (with-redefs [spire.scp/scp-from no-scp]
           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download {:src test-dir :dest tf :recurse true :preserve true})))
           (is (= (test-utils/run (format "cd \"%s/localhost/files\" && find . -exec stat -c \"%%s %%a %%Y %%X %%F %%n\" {} \\;" tf))
                  (test-utils/ssh-run (format "cd \"%s\" && find . -exec stat -c \"%%s %%a %%Y %%X %%F %%n\" {} \\;" test-dir)))))

         ;; copy with preserve to begin with
         (test-utils/makedirs tf2)
         (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                (download {:src test-dir :dest tf2 :recurse true :preserve true})))
         (is (= (test-utils/run (format "cd \"%s/localhost/files\" && find . -exec stat -c \"%%s %%a %%Y %%X %%F %%n\" {} \\;" tf2))
                (test-utils/ssh-run (format "cd \"%s\" && find . -exec stat -c \"%%s %%a %%Y %%X %%F %%n\" {} \\;" test-dir))))
         (is (= (test-utils/run (format "cd \"%s/localhost/files\" && find . -type f -exec md5sum {} \\;" tf2))
                (test-utils/ssh-run (format "cd \"%s\" && find . -type f -exec md5sum {} \\;" test-dir))))





         )))))
