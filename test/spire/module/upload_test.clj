(ns spire.module.upload-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [spire.module.upload :refer :all]
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

(deftest upload-test
  (testing "upload test"
    (test-utils/with-temp-file-names [tf tf2]
      (transport/ssh
       "localhost"
       ;; copy file
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files/copy/test.txt" :dest tf})))
       (is (= (slurp "test/files/copy/test.txt") (slurp tf)))

       (with-redefs [spire.scp/scp-to no-scp]
         ;; second copy doesn't transfer files
         (is (= {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}
                (upload {:src "test/files/copy/test.txt" :dest tf})))
         (is (= (slurp "test/files/copy/test.txt") (slurp tf)))

         ;; reupload changes file modes with :changed
         (is (= {:result :changed, :attr-result {:result :changed} :copy-result {:result :ok}}
                (upload {:src "test/files/copy/test.txt" :dest tf :mode 0777})))
         (is (= "777" (test-utils/mode tf)))

         ;; and repeat doesn't change anything
         (is (= {:result :ok, :attr-result {:result :ok} :copy-result {:result :ok}}
                (upload {:src "test/files/copy/test.txt" :dest tf :mode 0777})))
         (is (= "777" (test-utils/mode tf))))

       ;; try and copy directory without recurse
       (is (= {:exit 3, :out "", :err ":recurse must be true when :src specifies a directory.", :result :failed}
              (upload {:src "test/files" :dest tf})))

       ;; try and copy directory over existing file without :force
       (is (= {:result :failed, :attr-result {:result :ok},
               :copy-result
               {:result :failed, :err "Cannot copy `content` directory over `dest`: destination is a file. Use :force to delete destination file and replace."}}
              (upload {:src "test/files" :dest tf :recurse true})))

       ;; force copy dir over file
       (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :changed}}
              (upload {:src "test/files" :dest tf :recurse true :force true :mode 0777})))
       (is (= (test-utils/run "cd test/files && find . -exec stat -c \"%s %F %n\" {} \\;")
              (test-utils/ssh-run (format "cd \"%s\" && find . -exec stat -c \"%%s %%F %%n\" {} \\;" tf))))
       (is (= (test-utils/run "cd test/files && find . -type f -exec md5sum {} \\;")
              (test-utils/ssh-run (format "cd \"%s\" && find . -type f -exec md5sum {} \\;" tf))))

       ;; recopy dir but with :preserve
       (with-redefs [spire.scp/scp-to no-scp]
         (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                (upload {:src "test/files" :dest tf :recurse true :force true :preserve true})))
         (is (= (test-utils/run "cd test/files && find . -exec stat -c \"%s %a %Y %X %F %n\" {} \\;")
                (test-utils/ssh-run (format "cd \"%s\" && find . -exec stat -c \"%%s %%a %%Y %%X %%F %%n\" {} \\;" tf)))))

       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files" :dest tf2 :recurse true :preserve true})))
       (is (= (test-utils/run "cd test/files && find . -exec stat -c \"%s %a %Y %X %F %n\" {} \\;")
              (test-utils/ssh-run (format "cd \"%s\" && find . -exec stat -c \"%%s %%a %%Y %%X %%F %%n\" {} \\;" tf2))))))))
