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
    (test-utils/with-temp-file-names [tf tf2 tf3 tf4]
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

         ;; reupload changed file modes with :changed
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
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
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

       ;; preserve copy from scratch
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files" :dest tf2 :recurse true :preserve true})))
       (is (= (test-utils/run "cd test/files && find . -exec stat -c \"%s %a %Y %X %F %n\" {} \\;")
              (test-utils/ssh-run (format "cd \"%s\" && find . -exec stat -c \"%%s %%a %%Y %%X %%F %%n\" {} \\;" tf2))))

       ;; mode and dir-mode from scratch
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files" :dest tf3 :recurse true :mode 0666 :dir-mode 0777})))
       (is (= (test-utils/run "cd test/files && find . -type f -exec stat -c \"%s 666 %F %n\" {} \\;")
              (test-utils/ssh-run (format "cd \"%s\" && find . -type f -exec stat -c \"%%s %%a %%F %%n\" {} \\;" tf3))))
       (is (= (test-utils/run "cd test/files && find . -type d -exec stat -c \"%s 777 %F %n\" {} \\;")
              (test-utils/ssh-run (format "cd \"%s\" && find . -type d -exec stat -c \"%%s %%a %%F %%n\" {} \\;" tf3))))

       (with-redefs [spire.scp/scp-to no-scp]
         ;; redo copy but change mode and dir-mode
         (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                (upload {:src "test/files" :dest tf3 :recurse true :mode 0644 :dir-mode 0755})))
         (is (= (test-utils/run "cd test/files && find . -type f -exec stat -c \"%s 644 %F %n\" {} \\;")
                (test-utils/ssh-run (format "cd \"%s\" && find . -type f -exec stat -c \"%%s %%a %%F %%n\" {} \\;" tf3))))
         (is (= (test-utils/run "cd test/files && find . -type d -exec stat -c \"%s 755 %F %n\" {} \\;")
                (test-utils/ssh-run (format "cd \"%s\" && find . -type d -exec stat -c \"%%s %%a %%F %%n\" {} \\;" tf3))))
         )

       ;; copy with unexecutable directory
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload {:src "test/files" :dest tf4 :recurse true :mode 0 :dir-mode 0})))
       ;; will need root just to check this directory
       (transport/ssh
        "root@localhost"
        (is (= (test-utils/run "cd test/files && find . -exec stat -c \"%s 0 %F %n\" {} \\;")
               (test-utils/ssh-run (format "cd \"%s\" && find . -exec stat -c \"%%s %%a %%F %%n\" {} \\;" tf4))))
        ;; the with-temp-file-names macro wont be able to delete this, so lets do it now while we are root
        ;; TODO: when implementing testing on another target system, the with-temp-file-names could
        ;; be made to do remote deletion
        (test-utils/ssh-run (format "rm -rf \"%s\"" tf4))
        )))))
