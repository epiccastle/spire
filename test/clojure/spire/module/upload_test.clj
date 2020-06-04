(ns spire.module.upload-test
  (:require [clojure.test :refer :all]
            [spire.test-config :as test-config]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [spire.module.upload :as upload]
            [spire.module.attrs :as attrs]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.state :as state]
            [spire.config :as config]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]
            [spire.output.default]))

(clojure.lang.RT/loadLibrary "spire")

(defn no-scp [& args]
  (assert false "second copy should be skipped"))

(deftest upload-test
  (testing "upload test"
    (test-utils/with-temp-file-names [tf tf2 tf3 tf4]
      (transport/ssh
       test-config/localhost
       ;; copy file
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload/upload {:src "test/files/copy/test.txt" :dest tf})))
       (is (test-utils/files-md5-match? "test/files/copy/test.txt" tf))

       (with-redefs [spire.scp/scp-to no-scp]
         ;; second copy doesn't transfer files
         (is (= {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}
                (upload/upload {:src "test/files/copy/test.txt" :dest tf})))
         (is (test-utils/files-md5-match? "test/files/copy/test.txt" tf))

         ;; reupload/upload changed file modes with :changed
         (is (= {:result :changed, :attr-result {:result :changed} :copy-result {:result :ok}}
                (upload/upload {:src "test/files/copy/test.txt" :dest tf :mode 0777})))
         (is (= "777" (test-utils/mode tf)))

         ;; and repeat doesn't change anything
         (is (= {:result :ok, :attr-result {:result :ok} :copy-result {:result :ok}}
                (upload/upload {:src "test/files/copy/test.txt" :dest tf :mode 0777})))
         (is (= "777" (test-utils/mode tf))))

       ;; try and copy directory without recurse
       (test-utils/should-ex-data
        {:exit 3
         :out ""
         :err ":recurse must be true when :src specifies a directory."
         :result :failed}
        (upload/upload {:src "test/files" :dest tf}))

       ;; try and copy directory over existing file without :force
       (test-utils/should-ex-data
        {:result :failed,
                  :attr-result {:result :ok},
                  :copy-result {:result :failed, :err "Cannot copy `content` directory over `dest`: destination is a file. Use :force to delete destination file and replace."}}
        (upload/upload {:src "test/files" :dest tf :recurse true}))

       ;; force copy dir over file
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload/upload {:src "test/files" :dest tf :recurse true :force true :mode 0777})))
       (is (test-utils/recurse-file-size-type-name-match? "test/files" tf))
       (is (test-utils/find-files-md5-match? "test/files" tf))

       ;; recopy dir but with :preserve
       (with-redefs [spire.scp/scp-to no-scp]
         (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                (upload/upload {:src "test/files" :dest tf :recurse true :force true :preserve true})))
         (is (test-utils/recurse-file-size-type-name-mode-match? "test/files" tf))
         ;; (is (test-utils/recurse-access-modified-match? "test/files" tf))
         )

       ;; preserve copy from scratch
       (let [result (upload/upload {:src "test/files" :dest tf2 :recurse true :preserve true})]
         (is
          (or
           (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              result)
           (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :changed}}
              result))))

       (is (test-utils/recurse-file-size-type-name-mode-match? "test/files" tf2))
       ;; (is (test-utils/recurse-access-modified-match? "test/files" tf2))

       ;; mode and dir-mode from scratch
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload/upload {:src "test/files" :dest tf3 :recurse true :mode 0666 :dir-mode 0777})))
       (is (test-utils/recurse-file-size-type-name-match? "test/files" tf3))
       (is (test-utils/find-local-files-mode-is? tf3 "666"))
       (is (test-utils/find-local-dirs-mode-is? tf3 "777"))

       (with-redefs [spire.scp/scp-to no-scp]
         ;; redo copy but change mode and dir-mode
         (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                (upload/upload {:src "test/files" :dest tf3 :recurse true :mode 0644 :dir-mode 0755})))
         (is (test-utils/recurse-file-size-type-name-match? "test/files" tf3))
         (is (test-utils/find-local-files-mode-is? tf3 "644"))
         (is (test-utils/find-local-dirs-mode-is? tf3 "755")))

       ;; copy with unexecutable directory
       (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
              (upload/upload {:src "test/files" :dest tf4 :recurse true :mode 0 :dir-mode 0})))
       ;; will need root just to check this directory
       (transport/ssh
        test-config/localhost-root
        (is (test-utils/recurse-file-size-type-name-match? "test/files" tf4))
        (is (test-utils/find-remote-files-mode-is? tf4 "0"))
        (is (test-utils/find-remote-dirs-mode-is? tf4 "0"))

        ;; the with-temp-file-names macro wont be able to delete this, so lets do it now while we are root
        ;; TODO: when implementing testing on another target system, the with-temp-file-names could
        ;; be made to do remote deletion
        (test-utils/ssh-run (format "rm -rf \"%s\"" tf4)))))))

(deftest upload-idempotent-test
  (testing "uploads are idempotent - #74"
    (let [path-a "/tmp/spire-upload-test-recv-a"
          path-b "/tmp/spire-upload-test-recv-b"]
      (test-utils/remove-file path-a)
      (is (= (-> (upload/upload {:dest path-a :src "test" :recurse true :preserve true})
                 (select-keys [:result :copy-result]))
             {:result :changed :copy-result {:result :changed}}
             ))

      (test-utils/remove-file path-b)
      (test-utils/makedirs path-b)
      (is (= (upload/upload {:dest path-b :src "test" :recurse true :preserve true})
             ;; attr result is :changed because root dir is not created by copy
             ;; and needs its attr set
             {:result :changed, :attr-result {:result :changed}, :copy-result {:result :changed}}
             ))

      (is (= (test-utils/run (format "cd '%s'; find ." path-a))
             (test-utils/run (format "cd '%s'; find ." path-b))))

      ;; remove one file from path-b and recopy
      (test-utils/remove-file (str path-b "/config/sshd_config"))
      (is (= (-> (upload/upload {:dest path-b :src "test" :recurse true :preserve true})
                 (select-keys [:result :copy-result]))
             {:result :changed :copy-result {:result :changed}}))
      (is (= (test-utils/run (format "cd '%s'; find ." path-a))
             (test-utils/run (format "cd '%s'; find ." path-b)))))))

(deftest upload-absolute-path
  (testing "no permissions to write to :dest"
    (let [src-path "/tmp/spire-upload-abs-src"
          dest-path "/tmp/spire-upload-abs-dest"]
      (spit src-path "foo")
      (test-utils/remove-file dest-path)
      (is (= (upload/upload {:src src-path :dest dest-path})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= (slurp dest-path) "foo")))))

(deftest upload-content
  (testing "upload :content works"
    (let [dest "/tmp/spire-upload-test-content"
          content "this is a test"
          content-2 "new content"]
      (test-utils/remove-file dest)
      (is (= (upload/upload {:dest dest :content content})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= content (slurp dest)))
      (is (= (upload/upload {:dest dest :content content})
             {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}))
      (is (= content (slurp dest)))
      (is (= (upload/upload {:dest dest :content content-2})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= content-2 (slurp dest)))))

  (testing "upload :content io/file works"
    (let [dest "/tmp/spire-upload-test-content"
          src "/tmp/spire-upload-test-content-src"
          content "this is a test"
          content-2 "new content"]
      (test-utils/remove-file dest)
      (spit src content)
      (is (= (upload/upload {:dest dest :content (io/file src)})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= content (slurp dest)))
      (is (= (upload/upload {:dest dest :content (io/file src)})
             {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}))
      (is (= content (slurp dest)))
      (spit src content-2)
      (is (= (upload/upload {:dest dest :content (io/file src)})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= content-2 (slurp dest)))))

  (testing "upload :content byte array works"
    (let [dest "/tmp/spire-upload-test-content"
          content (byte-array (range 255))
          content-2 (byte-array (range 255 0 -1))]
      (test-utils/remove-file dest)
      (is (= (upload/upload {:dest dest :content content})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= (seq content) (seq (test-utils/slurp-bytes dest))))
      (is (= (upload/upload {:dest dest :content content})
             {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}))
      (is (= (seq content) (seq (test-utils/slurp-bytes dest))))
      (is (= (upload/upload {:dest dest :content content-2})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= (seq content-2) (seq (test-utils/slurp-bytes dest))))
      ))

  (testing "upload :content binary io/file works"
    (let [dest "/tmp/spire-upload-test-content"
          src "/tmp/spire-upload-test-content-src"
          content (byte-array (range 255))
          content-2 (byte-array (range 255 0 -1))]
      (test-utils/remove-file dest)
      (test-utils/spit-bytes src content)
      (is (= (upload/upload {:dest dest :content (io/file src)})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= (seq content) (seq (test-utils/slurp-bytes dest))))
      (is (= (upload/upload {:dest dest :content (io/file src)})
             {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}))
      (is (= (seq content) (seq (test-utils/slurp-bytes dest))))
      (test-utils/spit-bytes src content-2)
      (is (= (upload/upload {:dest dest :content (io/file src)})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= (seq content-2) (seq (test-utils/slurp-bytes dest))))))


  )

(deftest upload-errors
  (testing ":src is not found"
    (is (thrown? java.io.FileNotFoundException
                 (upload/upload {:src "jgtwojgtwtowprvow" :dest "/tmp/spire-upload-src-not-found"}))))
  (testing ":src is a folder but no recurse"
    (let [path "/tmp/spire-upload-src-directory-not-recursive"]
      (test-utils/remove-file path)
      (test-utils/makedirs path)
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"module failed"
                            (upload/upload {:src path :dest (str path "-dest")})))
      (try
        (upload/upload {:src path :dest (str path "-dest")})
        (catch clojure.lang.ExceptionInfo e
          (is (= ":recurse must be true when :src specifies a directory." (:err (ex-data e))))))))

  (testing ":dest does not exist"
    (let [path "/tmp/spire-upload-dest-not-exist"]
      (spit path "foo")
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"module failed"
                            (upload/upload {:src path :dest "/tmp/foo/bar/baz"})))
      (try
        (upload/upload {:src path :dest "/tmp/foo/bar/baz"})
        (catch clojure.lang.ExceptionInfo e
          (is (= "destination path unwritable" (:err (ex-data e))))))))

  (testing "no permissions to write to :dest"
    (let [src-path "/tmp/spire-upload-dest-perms-src"
          dest-path "/tmp/spire-upload-dest-perms-dest"]
      (spit src-path "foo")
      (test-utils/remove-file dest-path)
      (test-utils/makedirs dest-path)
      (test-utils/run (format "chmod a-w '%s'" dest-path))

      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"module failed"
                            (upload/upload {:src src-path :dest (str dest-path "/")})))

      (try
        (upload/upload {:src src-path :dest (str dest-path "/")})
        (catch clojure.lang.ExceptionInfo e
          (is (= "destination path unwritable" (:err (ex-data e))))))))

  (testing "directoy :dest trying to become file :src"
    (let [src-path "/tmp/spire-upload-dest-dir-becomes-file-src"
          dest-path "/tmp/spire-upload-dest-dir-becomes-file-dest"]
      (spit src-path "foo")
      (test-utils/remove-file dest-path)
      (test-utils/makedirs dest-path)
      ;;(test-utils/run (format "chmod a-w '%s'" dest-path))

      (is (thrown? clojure.lang.ExceptionInfo
                   (upload/upload {:src src-path :dest dest-path})))

      (try
        (upload/upload {:src src-path :dest dest-path})
        (catch clojure.lang.ExceptionInfo e
          (is (= (:err (ex-data e))
                 ":src is a single file while :dest is a folder. Append '/' to dest to write into directory or set :force to true to delete destination folder and write as file."))))

      (is (= (upload/upload {:src src-path :dest dest-path :force true})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
             ))



)))
