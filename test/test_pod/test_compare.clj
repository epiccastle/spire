(ns test-pod.test-compare
  (:require [test-pod.utils :refer [bash]]
            [test-pod.conf :as conf]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.compare :as compare]

            [clojure.test :refer [is deftest]]
            ))

(defn preflight []
  (bash "sudo rm -rf /tmp/test.txt /tmp/spire-compare")
  (bash "echo foo > /tmp/test.txt")
  (bash "mkdir -p /tmp/spire-compare/a /tmp/spire-compare/b /tmp/spire-compare/a/c")
  (bash "echo foo > /tmp/spire-compare/a/c/test-c.txt")
  (bash "echo foo > /tmp/spire-compare/a/test-a.txt")
  (bash "echo foo > /tmp/spire-compare/b/test-b.txt")
  )

(deftest compare-paths
  (preflight)
  (let [{:keys [remote local]} (compare/compare-paths "/tmp/test.txt" bash "/tmp/test.txt")]
    (is (= [""] (keys remote)))
    (is (= [""] (keys local)))

    (doseq [k [:type :filename :md5sum :mode :mode-string :last-modified]]
      (is (= (get-in remote ["" k])
             (get-in local ["" k])))))

  (let [{:keys [remote local]} (compare/compare-paths "/tmp/spire-compare" bash "/tmp/spire-compare")]
    (is (= #{"" "b" "a" "a/c" "b/test-b.txt" "a/c/test-c.txt" "a/test-a.txt"}
           (into #{} (keys remote))))
    (is (= #{"" "b" "a" "a/c" "b/test-b.txt" "a/c/test-c.txt" "a/test-a.txt"}
           (into #{} (keys local))))

    (doseq [f ["" "b" "a" "a/c" "b/test-b.txt" "a/c/test-c.txt" "a/test-a.txt"]]
      (doseq [k [:type :filename :md5sum :mode :mode-string :last-modified]]
      (is (= (get-in remote [f k])
             (get-in local [f k])))))))

(deftest compare-full-info
  (preflight)
  (let [{:keys [remote
                local
                identical-content
                local-to-remote
                remote-to-local]} (compare/compare-full-info "/tmp/test.txt" bash "/tmp/test.txt")]
    (is (= #{} local-to-remote))
    (is (= #{} remote-to-local))
    (is (= #{""} identical-content))

    )
  (let [{:keys [remote
                local
                identical-content
                local-to-remote
                remote-to-local]} (compare/compare-full-info "/tmp/spire-compare" bash "/tmp/spire-compare")]
    (is (= #{} local-to-remote))
    (is (= #{} remote-to-local))
    (is (= #{"a/c/test-c.txt" "a/test-a.txt" "b/test-b.txt"} identical-content))
    )
  )
