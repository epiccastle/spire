(ns spire.module.download-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.shell :as shell]
            [clojure.java.io :as io]
            [spire.module.download :as download]
            [spire.module.attrs :as attrs]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.state :as state]
            [spire.config :as config]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]
            [spire.test-config :as test-config]
            [spire.output.default]
            ))

(clojure.lang.RT/loadLibrary "spire")

(defn no-scp [& args]
  (assert false "second copy should be skipped"))

(defn pwd []
  (let [{:keys [exit err out]} (shell/sh "pwd")]
    (assert (zero? exit))
    (string/trim out)))

#_(deftest download-test
  (testing "download test"
    (let [test-dir (str (io/file (pwd) "test/files"))]
      (test-utils/with-temp-file-names [tf tf2 tf3]
        (test-utils/makedirs tf)
        (transport/ssh
         test-config/localhost
         ;; copy directory recursively
         (is (= {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
                (download/download {:src test-dir :dest tf :recurse true})))
         (is (test-utils/recurse-file-size-type-name-match? (str tf "/localhost/files") test-dir))
         (is (test-utils/find-files-md5-match? (str tf "/localhost/files") test-dir))

         (with-redefs [spire.scp/scp-from no-scp]
           ;; reset attrs with hard coded mode and dir-mode
           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download/download {:src test-dir :dest tf :recurse true :dir-mode 0777 :mode 0666})))
           (is (test-utils/recurse-file-size-type-name-match? (str tf "/localhost/files") test-dir))
           (is (test-utils/find-local-files-mode-is? (str tf "/localhost/files") "666"))
           (is (test-utils/find-local-dirs-mode-is? (str tf "/localhost/files") "777"))

           ;; reset attrs with :preserve
           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download/download {:src test-dir :dest tf :recurse true :preserve true})))
           (is (test-utils/recurse-file-size-type-name-mode-match? (str tf "/localhost/files") test-dir))
           #_ (is (test-utils/recurse-access-modified-match? (str tf "/localhost/files") test-dir)))

         ;; copy with preserve to begin with
         (test-utils/makedirs tf2)
         (let [result (download/download {:src test-dir :dest tf2 :recurse true :preserve true})]
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
                (download/download {:src (io/file test-dir "copy/test.txt") :dest tf3})))
         (is (test-utils/files-md5-match? (str tf3 "/localhost/test.txt") (str test-dir "/copy/test.txt")))

         (with-redefs [spire.scp/scp-from no-scp]
           (is (= {:result :ok, :attr-result {:result :ok}, :copy-result {:result :ok}}
                  (download/download {:src (io/file test-dir "copy/test.txt") :dest tf3})))
           (is (test-utils/files-md5-match? (str tf3 "/localhost/test.txt") (str test-dir "/copy/test.txt")))

           (is (= {:result :changed, :attr-result {:result :changed}, :copy-result {:result :ok}}
                  (download/download {:src (io/file test-dir "copy/test.txt") :dest tf3 :mode 0666})))

           (is (= "43 666"
                  (test-utils/stat-local (str tf3 "/localhost/test.txt") ["%s" "%a"]))))
         )))))

(deftest download-test-2
  (testing "download local tests"
    (let [path-a "/tmp/spire-download-test-recv-a"
          path-b "/tmp/spire-download-test-recv-b"]
      (test-utils/remove-file path-a)
      (test-utils/makedirs path-a)
      (is (thrown? clojure.lang.ExceptionInfo (download/download {:src "test" :dest path-a})))
      (try (download/download {:src "test" :dest path-a})
           (catch clojure.lang.ExceptionInfo e
             (is (= (:err (ex-data e))
                    ":recurse must be true when :src specifies a directory."))))

      (is (= (download/download {:src "test" :dest path-a :recurse true})
             {:result :changed :attr-result {:result :ok} :copy-result {:result :changed}}))

      (test-utils/remove-file path-b)
      (test-utils/makedirs path-b)
      (is (= (download/download {:dest path-b :src "test" :recurse true})
             {:result :changed :attr-result {:result :ok} :copy-result {:result :changed}}))

      ;; (println "path-a")
      ;; (println (test-utils/run (format "cd '%s'; find ." path-a)))

      ;; (println "path-b")
      ;; (println (test-utils/run (format "cd '%s'; find ." path-b)))


      (is (= (test-utils/run (format "cd '%s'; find . -type f" path-a))
             (test-utils/run (format "cd '%s'; find . -type f" path-b)))))))

(deftest download-test-3
  (testing "download local tests"
    (let [src "/tmp/spire-download-test-3-src"
          dest "/tmp/spire-download-test-3-dest"]
      (test-utils/remove-file src)
      (test-utils/remove-file dest)
      (test-utils/makedirs (str src "/b"))
      (spit (str src "/a") "a")
      (test-utils/makedirs dest)
      (test-utils/ln-s "non-existent" (str src "/b/broken"))
      (is (= (download/download {:src src :dest dest :recurse true :flat true})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= "a" (slurp (str dest "/spire-download-test-3-src/a"))))
      (is (.exists (io/file (str dest "/spire-download-test-3-src/b"))))
      (is (.isDirectory (io/file (str dest "/spire-download-test-3-src/b"))))
      )))

(deftest download-test-4
  (testing "download local tests"
    (let [src "/tmp/spire-download-test-4-src"
          dest "/tmp/spire-download-test-4-dest"]
      (test-utils/remove-file src)
      (test-utils/remove-file dest)
      (test-utils/makedirs (str src "/a"))
      (test-utils/makedirs (str src "/a/b"))
      (test-utils/makedirs (str src "/a/c"))
      (test-utils/makedirs dest)
      (is (= (download/download {:src src :dest dest :recurse true :flat true})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
#_      (is (.exists (io/file (str dest "/local/spire-download-test-4-src/a"))))
#_      (is (.isDirectory (io/file (str dest "/local/spire-download-test-4-src/a"))))
      (is (.exists (io/file (str dest "/a"))))
      (is (.isDirectory (io/file (str dest "/a"))))
      (is (.exists (io/file (str dest "/a/b"))))
      (is (.isDirectory (io/file (str dest "/a/b"))))
      (is (.exists (io/file (str dest "/a/c"))))
      (is (.isDirectory (io/file (str dest "/a/c"))))
      )))

(deftest download-absolute-path
  (testing "download from/to an absolute path"
    (let [src-path "/tmp/spire-download-abs-src"
          dest-path "/tmp/spire-download-abs-dest"]
      (test-utils/remove-file src-path)
      (spit src-path "foo")
      (test-utils/remove-file dest-path)
      (is (= (download/download {:src src-path :dest dest-path :flat true})
             {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))
      (is (= (slurp (str dest-path "/spire-download-abs-src")) "foo")))
    ))

(deftest download-errors
  (testing ":src is not found"
    (is (thrown? clojure.lang.ExceptionInfo
                 (download/download {:src "jgtwojgtwtowprvow" :dest "/tmp/spire-download-src-not-found"})))
    (try (download/download {:src "jgtwojgtwtowprvow" :dest "/tmp/spire-download-src-not-found"})
         (catch clojure.lang.ExceptionInfo e
           (is (= (:err (ex-data e))
                  ":src path is unreadable on remote. Check path exists and is readable by user.")))))
  (testing ":src is a folder but no recurse"
    (let [path "/tmp/spire-download-src-directory-not-recursive"]
      (test-utils/remove-file path)
      (test-utils/makedirs path)
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"module failed"
                            (download/download {:src path :dest (str path "-dest")})))
      (try
        (download/download {:src path :dest (str path "-dest")})
        (catch clojure.lang.ExceptionInfo e
          (is (= ":recurse must be true when :src specifies a directory." (:err (ex-data e))))))))

  (testing ":dest does not exist"
    (let [path "/tmp/spire-download-dest-not-exist"]
      (spit path "foo")
      (test-utils/remove-file "/tmp/foo/bar")
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"module failed"
                            (download/download {:src path :dest "/tmp/foo/bar/baz"})))
      (try
        (download/download {:src path :dest "/tmp/foo/bar/baz" :recurse true})
        (catch clojure.lang.ExceptionInfo e
          (is (= "destination path :dest is unwritable" (:err (ex-data e))))))))

  #_(testing "no permissions to write to :dest"
      (let [src-path "/tmp/spire-download-dest-perms-src"
            dest-path "/tmp/spire-download-dest-perms-dest"]
        (spit src-path "foo")
        (test-utils/remove-file dest-path)
        (test-utils/makedirs dest-path)
        (test-utils/run (format "chmod a-w '%s'" dest-path))

        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"module failed"
                              (download/download {:src src-path :dest (str dest-path "/")})))

        (try
          (download/download {:src src-path :dest (str dest-path "/")})
          (catch clojure.lang.ExceptionInfo e
            (is (= "destination path unwritable" (:err (ex-data e))))))))

  #_(testing "directory :dest trying to become file :src"
      (let [src-path "/tmp/spire-download-dest-dir-becomes-file-src"
            dest-path "/tmp/spire-download-dest-dir-becomes-file-dest"]
        (spit src-path "foo")
        (test-utils/remove-file dest-path)
        (test-utils/makedirs dest-path)
        ;;(test-utils/run (format "chmod a-w '%s'" dest-path))

        (is (thrown? clojure.lang.ExceptionInfo
                     (download/download {:src src-path :dest dest-path})))

        (try
          (download/download {:src src-path :dest dest-path})
          (catch clojure.lang.ExceptionInfo e
            (is (= (:err (ex-data e))
                   ":src is a single file while :dest is a folder. Append '/' to dest to write into directory or set :force to true to delete destination folder and write as file."))))

        (is (= (download/download {:src src-path :dest dest-path :force true})
               {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}
               ))))

  #_(testing "directory :src trying to overwrite file :dest"
      (let [src-path "/tmp/spire-download-src-dir-becomes-file-src"
            dest-path "/tmp/spire-download-src-dir-becomes-file-dest"]
        (test-utils/remove-file dest-path)
        (spit dest-path "foo")
        (test-utils/remove-file src-path)
        (test-utils/makedirs src-path)
        (spit (str src-path "/a") "a")
        (spit (str src-path "/b") "b")

        ;;(test-utils/run (format "chmod a-w '%s'" dest-path))

        (is (thrown? clojure.lang.ExceptionInfo
                     (download/download {:src src-path :dest dest-path})))

        (try
          (download/download {:src src-path :dest dest-path})
          (catch clojure.lang.ExceptionInfo e
            (is (= (:err (ex-data e))
                   ":recurse must be true when :src specifies a directory."))))

        (is (thrown? clojure.lang.ExceptionInfo
                     (download/download {:src src-path :dest dest-path :recurse true})))

        (try
          (download/download {:src src-path :dest dest-path :recurse true})
          (catch clojure.lang.ExceptionInfo e
            (is (= (:err (ex-data e))
                   "Cannot copy :src directory over :dest. Destination is a file. Use :force to delete destination file and replace."))))

        (is (= (download/download {:src src-path :dest dest-path :recurse true :force true})
               {:result :changed, :attr-result {:result :ok}, :copy-result {:result :changed}}))

        (is (= "a" (slurp (str dest-path "/a"))))
        (is (= "b" (slurp (str dest-path "/b"))))))
  )




#_(deftest download-fine-permission
  (testing "copying tree down"
    (let [src-path "/tmp/spire-download-sync-src"
          dest-path "/tmp/spire-download-sync-dest"]
      (doseq [path [src-path]]
        (test-utils/remove-file path)
        (test-utils/makedirs path)
        (spit (str path "/a") "a")
        (spit (str path "/b") "b")
        (test-utils/makedirs (str path "/c"))
        (spit (str path "/c/d") "d"))

      (is (= (download/download {:src src-path :dest dest-path :recurse true})
             0))

      #_(is (= (download/download {:src src-path :dest dest-path :recurse true :force true})
             {:result :changed :attr-result {:result :ok} :copy-result {:result :changed}}))

      #_(is (= (slurp (str dest-path "/a")) "a"))
      #_(is (= (slurp (str dest-path "/b")) "b"))))

  #_ (testing "copying tree over existing copy with no permission for one file"
    (let [src-path "/tmp/spire-download-sync-src"
          dest-path "/tmp/spire-download-sync-dest"]
      (doseq [path [src-path dest-path]]
        (test-utils/remove-file path)
        (test-utils/makedirs path)
        (spit (str path "/a") "a")
        (spit (str path "/b") "b"))
      (spit (str dest-path "/a") "changed")
      (test-utils/run (format "chmod a-w '%s'" (str dest-path "/a")))

      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"scp error"
                            (download/download {:src src-path :dest dest-path :recurse true})))

      (try
        (download/download {:src src-path :dest dest-path :recurse true})
        (catch clojure.lang.ExceptionInfo e
          (is (re-find #"[pP]ermission denied" (:msg (ex-data e))))))

      (is (= (download/download {:src src-path :dest dest-path :recurse true :force true})
             {:result :changed :attr-result {:result :ok} :copy-result {:result :changed}}))

      (is (= (slurp (str dest-path "/a")) "a"))
      (is (= (slurp (str dest-path "/b")) "b"))))

  #_ (testing "copying tree over existing copy with changed perms only changes attrs"
    (let [src-path "/tmp/spire-download-sync2-src"
          dest-path "/tmp/spire-download-sync2-dest"]
      (doseq [path [src-path dest-path]]
        (test-utils/remove-file path)
        (test-utils/makedirs path)
        (spit (str path "/a") "a")
        (spit (str path "/b") "b"))

      (test-utils/run (format "chmod a+x '%s'" (str dest-path "/a")))

      (is (= (download/download {:src src-path :dest dest-path :recurse true :preserve true})
             {:result :changed :attr-result {:result :changed} :copy-result {:result :ok}}))

      (is (= (slurp (str dest-path "/a")) "a"))
      (is (= (slurp (str dest-path "/b")) "b"))

      (test-utils/run (format "chmod a+x '%s'" (str dest-path "/a")))

      (is (= (download/download {:src src-path :dest dest-path :recurse true :mode 0644 :dir-mode 0755})
             {:result :changed :attr-result {:result :changed} :copy-result {:result :ok}}))

      (is (= (slurp (str dest-path "/a")) "a"))
      (is (= (slurp (str dest-path "/b")) "b"))))

  #_ (testing "copying tree over without dir-mode breaks"
    (let [src-path "/tmp/spire-download-sync3-src"
          dest-path "/tmp/spire-download-sync3-dest"]
      (doseq [path [src-path dest-path]]
        (test-utils/remove-file path)
        (test-utils/makedirs path)
        (spit (str path "/a") "a")
        (spit (str path "/b") "b"))

      (test-utils/run (format "chmod a+x '%s'" (str dest-path "/a")))

      (is (= (download/download {:src src-path :dest dest-path :recurse true :mode 0644})
             {:result :changed :attr-result {:result :changed} :copy-result {:result :ok}}))

      (is (= (slurp (str dest-path "/a")) "a"))
      (is (= (slurp (str dest-path "/b")) "b")))
    )
  )
