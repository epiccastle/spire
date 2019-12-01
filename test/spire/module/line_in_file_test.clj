(ns spire.module.line-in-file-test
  (:require [clojure.test :refer :all]
            [clojure.java.shell :as shell]
            [spire.module.line-in-file :refer :all]))

(defn test-pipelines [func]
  (func "localhost" nil))

(defn test-ssh-exec [session command in out opts]
  ;;(println command)
  (shell/sh "bash" "-c" command :in in))

(deftest line-in-file-get-test
  (testing "line-in-file :get by line-num"
    (with-redefs [spire.transport/pipelines test-pipelines
                  spire.ssh/ssh-exec test-ssh-exec]
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/missing-file" :line-num 3})
           {:exit 1 :out "" :err "File not found." :result :failed}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num 1})
           {:exit 0
            :result :ok
            :line-num 1
            :line "This is line #1"
            :line-nums [1]
            :lines ["This is line #1"]
            :matches {1 "This is line #1"}}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num 5})
           {:exit 0
            :result :ok
            :line-num 5
            :line "This is line #5"
            :line-nums [5]
            :lines ["This is line #5"]
            :matches {5 "This is line #5"}}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num -1})
           {:exit 0
            :result :ok
            :line-num 10
            :line "This is line #10"
            :line-nums [10]
            :lines ["This is line #10"]
            :matches {10 "This is line #10"}}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num 10})
           {:exit 0
            :result :ok
            :line-num 10
            :line "This is line #10"
            :line-nums [10]
            :lines ["This is line #10"]
            :matches {10 "This is line #10"}}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num -3})
           {:exit 0
            :result :ok
            :line-num 8
            :line "This is line #8"
            :line-nums [8]
            :lines ["This is line #8"]
            :matches {8 "This is line #8"}}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num -10})
           {:exit 0
            :result :ok
            :line-num 1
            :line "This is line #1"
            :line-nums [1]
            :lines ["This is line #1"]
            :matches {1 "This is line #1"}}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num 0})
           {:exit 2 :out "" :err "No line number 0 in file. File line numbers are 1 offset." :result :failed}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num 20})
           {:exit 2 :out "" :err "No line number 20 in file." :result :failed}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/simple-file.txt" :line-num -20})
           {:exit 2 :out "" :err "No line number -20 in file." :result :failed}))))

  (testing "line-in-file :get by regexp"
    (with-redefs [spire.transport/pipelines test-pipelines
                  spire.ssh/ssh-exec test-ssh-exec]
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/regexp-file.txt" :regexp #"no such line"})
           {:exit 0
            :result :ok
            :line-num nil
            :line nil
            :line-nums []
            :lines []
            :matches {}}))
      (is (=
           (line-in-file* :get {:path "test/files/line-in-file/regexp-file.txt" :regexp #"and it contains"})
           {:exit 0
            :result :ok
            :line-num 19
            :line "This is line #19 and it contains a [ character"
            :line-nums [2 4 5 9 12 13 15 18 19]
            :lines ["This is line #2 and it contains a \\ character"
                    "This is line #4 and it contains a ' character"
                    "This is line #5 and it contains a \" character"
                    "This is line #9 and it contains a / character"
                    "This is line #12 and it contains a . character"
                    "This is line #13 and it contains a * character"
                    "This is line #15 and it contains a $ character"
                    "This is line #18 and it contains a | character"
                    "This is line #19 and it contains a [ character"
                    ]
            :matches {2 "This is line #2 and it contains a \\ character"
                      4 "This is line #4 and it contains a ' character"
                      5 "This is line #5 and it contains a \" character"
                      9 "This is line #9 and it contains a / character"
                      12 "This is line #12 and it contains a . character"
                      13 "This is line #13 and it contains a * character"
                      15 "This is line #15 and it contains a $ character"
                      18 "This is line #18 and it contains a | character"
                      19 "This is line #19 and it contains a [ character"}})))))
