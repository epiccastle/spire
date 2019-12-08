(ns spire.module.copy-test
  (:require [clojure.test :refer :all]
            [clojure.java.shell :as shell]
            [spire.module.line-in-file :refer :all]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn test-pipelines [func]
  (func "localhost" nil))

(defn test-ssh-exec [session command in out opts]
  ;;(println command)
  (shell/sh "bash" "-c" command :in in))

(defn test-scp-to [session local-paths remote-path]
  ;; just handles a single file for now
  (io/copy (io/file local-paths) (io/file remote-path)))

(deftest copy-test
  (testing "copy test"
    (with-redefs [spire.transport/pipelines test-pipelines
                  spire.ssh/ssh-exec test-ssh-exec
                  spire.scp/scp-to test-scp-to
                  spire.output/print-form identity]
      (is (= 1 1)))
    ))
