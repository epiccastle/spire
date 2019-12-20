(ns spire.compare-test
  (:require [clojure.test :refer :all]
            [spire.module.upload :refer :all]
            [spire.module.attrs :refer :all]
            [spire.compare :as compare]
            [spire.utils :as utils]
            [spire.nio :as nio]
            [clojure.java.io :as io]
            [spire.test-utils :as test-utils]))

(clojure.lang.RT/loadLibrary "spire")

(def test-files-info
  {"line-in-file/simple-file.txt" {:type :f
                                   :filename "line-in-file/simple-file.txt",
                                   :md5sum "26f1e459be7111918b0be22aa793b459",
                                   :mode 436,
                                   :mode-string "664",
                                   :last-access (nio/last-access-time "test/files/line-in-file/simple-file.txt"),
                                   :last-modified (nio/last-modified-time "test/files/line-in-file/simple-file.txt"),
                                   :size 161},
   "line-in-file/regexp-file.txt" {:type :f
                                   :filename "line-in-file/regexp-file.txt",
                                   :md5sum "7556dd19966458ae01f51b92c4512ed4",
                                   :mode 436,
                                   :mode-string "664",
                                   :last-access (nio/last-access-time "test/files/line-in-file/regexp-file.txt"),
                                   :last-modified (nio/last-modified-time "test/files/line-in-file/regexp-file.txt"),
                                   :size 601},
   "copy/test.txt" {:type :f
                    :filename "copy/test.txt",
                    :md5sum "51ec9f91f697e5b321534c705ebbdcf5",
                    :mode 436,
                    :mode-string "664",
                    :last-access (nio/last-access-time "test/files/copy/test.txt"),
                    :last-modified (nio/last-modified-time "test/files/copy/test.txt"),
                    :size 43}})

(deftest compare-test
  (testing "compare directory to directory"
    (test-utils/with-temp-file-names [t1 empty-dir]
      (test-utils/makedirs t1)
      (let [{:keys [local remote
                    identical-content
                    local-to-remote
                    remote-to-local]} (compare/compare-full-info "test/files" test-utils/run t1)]
        (is (= (select-keys local (keys test-files-info)) test-files-info))
        (is (empty? remote))
        (is (empty? identical-content))
        (is (local-to-remote "line-in-file/simple-file.txt"))
        (is (local-to-remote "copy/test.txt"))
        (is (local-to-remote "line-in-file/regexp-file.txt"))
        (is (empty? remote-to-local)))

      (test-utils/run (format "cp -av test/files/* %s" t1))

      (let [{:keys [local remote
                    identical-content
                    local-to-remote
                    remote-to-local]} (compare/compare-full-info "test/files" test-utils/run t1)]
        (is (= (select-keys local (keys test-files-info)) test-files-info))
        (is (= (select-keys remote (keys test-files-info)) test-files-info))
        (is (identical-content "line-in-file/simple-file.txt"))
        (is (identical-content "line-in-file/regexp-file.txt"))
        (is (identical-content "copy/test.txt"))
        (is (empty? local-to-remote))
        (is (empty? remote-to-local)))

      (test-utils/makedirs empty-dir)
      (test-utils/run (format "cp -av test/files/* %s" t1))

      (let [{:keys [local remote
                    identical-content
                    local-to-remote
                    remote-to-local]} (compare/compare-full-info empty-dir test-utils/run t1)]
        (is (empty? local))
        (is (= (select-keys remote (keys test-files-info)) test-files-info))
        (is (empty? identical-content))
        (is (empty? local-to-remote))
        (is (remote-to-local "line-in-file/simple-file.txt"))
        (is (remote-to-local "copy/test.txt"))
        (is (remote-to-local "line-in-file/regexp-file.txt")))

      ;; alter one file in t1
      (test-utils/run (format "cp -av test/files/* %s" t1))
      (spit (io/file t1 "copy/test.txt") "Extra Line" :append true)

      (let [{:keys [local remote
                    identical-content
                    local-to-remote
                    remote-to-local] :as comparison}
            (compare/compare-full-info "test/files" test-utils/run t1)
            same-files ["line-in-file/simple-file.txt" "line-in-file/regexp-file.txt"]
            different-file "copy/test.txt"
            ]
        (is (= (select-keys local same-files) (select-keys test-files-info same-files)))
        (is (= (select-keys remote same-files) (select-keys test-files-info same-files)))
        (is (identical-content "line-in-file/simple-file.txt"))
        (is (identical-content "line-in-file/regexp-file.txt"))
        (is (= #{different-file} local-to-remote))
        (is (= #{different-file} remote-to-local))

        ;; extracting files to copy and their sizes
        (is (= (compare/local-to-remote comparison)
               {:sizes {"copy/test.txt" 43}, :total 43}))
        (is (= (compare/remote-to-local comparison)
               {:sizes {"copy/test.txt" 53}, :total 53}))))))
