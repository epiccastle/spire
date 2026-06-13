(ns spire.module.line-in-file-test
  (:require [clojure.test :refer :all]
            [spire.test-utils :as test-utils]
            [spire.test-config :as test-config]
            [spire.module.line-in-file :refer :all]
            [spire.transport :as transport]
            [clojure.java.io :as io]))

(deftest line-in-file-get-test

  ;;
  ;; :get {:line-num ...}
  ;;
  (testing "line-in-file :get by line-num"
    (transport/ssh
     test-config/localhost
     (try
       (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/missing-file")) :line-num 3})

       (catch clojure.lang.ExceptionInfo e
         (is (= (ex-data e) {:exit 1 :out "" :err "File not found." :result :failed}))))

     (is (=
          (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num 1})
          {:exit 0
           :result :ok
           :line-num 1
           :line "This is line #1"
           :line-nums [1]
           :lines ["This is line #1"]
           :matches {1 "This is line #1"}}))

     (is (=
          (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num 5})
          {:exit 0
           :result :ok
           :line-num 5
           :line "This is line #5"
           :line-nums [5]
           :lines ["This is line #5"]
           :matches {5 "This is line #5"}}))
     (is (=
          (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num -1})
          {:exit 0
           :result :ok
           :line-num 10
           :line "This is line #10"
           :line-nums [10]
           :lines ["This is line #10"]
           :matches {10 "This is line #10"}}))
     (is (=
          (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num 10})
          {:exit 0
           :result :ok
           :line-num 10
           :line "This is line #10"
           :line-nums [10]
           :lines ["This is line #10"]
           :matches {10 "This is line #10"}}))
     (is (=
          (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num -3})
          {:exit 0
           :result :ok
           :line-num 8
           :line "This is line #8"
           :line-nums [8]
           :lines ["This is line #8"]
           :matches {8 "This is line #8"}}))
     (is (=
          (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num -10})
          {:exit 0
           :result :ok
           :line-num 1
           :line "This is line #1"
           :line-nums [1]
           :lines ["This is line #1"]
           :matches {1 "This is line #1"}}))
     (try
       (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num 0})
       (catch clojure.lang.ExceptionInfo e
         (is (= (ex-data e)#_
                {:exit 2 :out "" :err "No line number 0 in file. File line numbers are 1 offset." :result :failed}))))
     (try
       (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num 20})
       (catch clojure.lang.ExceptionInfo e
         (is (= (ex-data e)
                {:exit 2 :out "" :err "No line number 20 in file." :result :failed}))))
     (try
       (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/simple-file.txt")) :line-num -20})
       (catch clojure.lang.ExceptionInfo e
         (is (= (ex-data e)
                {:exit 2 :out "" :err "No line number -20 in file." :result :failed}))))))

  ;;
  ;; :get {:regexp ...}
  ;;
  (testing "line-in-file :get by regexp"
    (transport/ssh
     test-config/localhost
     (is (=
          (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/regexp-file.txt")) :regexp #"no such line"})
          {:exit 0
           :result :ok
           :line-num nil
           :line nil
           :line-nums []
           :lines []
           :matches {}}))
     (is (=
          (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/regexp-file.txt")) :regexp #"and it contains" :match :all})
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
                     19 "This is line #19 and it contains a [ character"}}))
     (is (=
             (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/regexp-file.txt")) :regexp #"and it contains" :match :first})
             {:exit 0
              :result :ok
              :line-num 2
              :line "This is line #2 and it contains a \\ character"
              :line-nums [2]
              :lines ["This is line #2 and it contains a \\ character"]
              :matches {2 "This is line #2 and it contains a \\ character"}}))

     ;; default for :match is :first
     (is (=
             (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/regexp-file.txt")) :regexp #"and it contains"})
             {:exit 0
              :result :ok
              :line-num 2
              :line "This is line #2 and it contains a \\ character"
              :line-nums [2]
              :lines ["This is line #2 and it contains a \\ character"]
              :matches {2 "This is line #2 and it contains a \\ character"}}))
     (is (=
             (line-in-file :get {:path (.getAbsolutePath (io/file "test/files/line-in-file/regexp-file.txt")) :regexp #"and it contains" :match :last})
             {:exit 0
              :result :ok
              :line-num 19
              :line "This is line #19 and it contains a [ character"
              :line-nums [19]
              :lines ["This is line #19 and it contains a [ character"]
              :matches {19 "This is line #19 and it contains a [ character"}})))))


(deftest line-in-file-present-test
  (testing "line-in-file :present by line-num"
    (transport/ssh
     test-config/localhost
     (try
       (line-in-file :present {:path (.getAbsolutePath (io/file "test/files/line-in-file/missing-file")) :line-num 3})
       (catch clojure.lang.ExceptionInfo e
         (is (= (ex-data e)
                {:exit 1 :out "" :err "File not found." :result :failed}))))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :line-num 3 :line "new line 3"})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
new line 3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))
     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (try
         (line-in-file :present {:path tmp :line-num 14 :line "new line 3"})
         (catch clojure.lang.ExceptionInfo e
           (is (= (ex-data e)
                  {:exit 2, :out "", :err "No line number 14 in file.", :result :failed}))))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (try
         (line-in-file :present {:path tmp :line-num -14 :line "new line 3"})
         (catch clojure.lang.ExceptionInfo e
           (is (= (ex-data e)
                  {:exit 2, :out "", :err "No line number -14 in file.", :result :failed}))))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))))

  ;;
  ;; :present {:regexp ...}
  ;;
  (testing "line-in-file :present by regexp"
    (transport/ssh
     test-config/localhost
     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"line #3" :line "new line 3"})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
new line 3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"line #3" :line "This is line #3"})
            {:exit 0 :out "" :err "" :result :ok}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line"})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
new line
")))
     #_ (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
          (is (=
               (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :insert-at :bof})
               {:exit 0 :out "" :err "" :result :changed}))
          (is (= (slurp tmp)
                 "new line
This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line that contains a \\ backslash and some '\"$^\t funny chars." :after #"line #8"})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
new line that contains a \\ backslash and some '\"$^\t funny chars.
This is line #9
This is line #10
"))
       )


     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :after #"line #8"})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
new line
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"line #9" :line "This is line #9" :after #"line #8"})
            {:exit 0 :out "" :err "" :result :ok}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :after #"line #1" :match :first})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
new line
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))
     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :after #"line #1" :match :last})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
new line
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :after #"line #1" :match :all})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
new line
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
new line
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "This is line #2" :after #"line #1" :match :all})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
This is line #2
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :before #"line #8"})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
new line
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "This is line #7" :before #"line #8"})
            {:exit 0 :out "" :err "" :result :ok}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :before #"line #1" :match :first})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "new line
This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :before #"line #1" :match :last})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
new line
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "This is line #9" :before #"line #1" :match :last})
            {:exit 0 :out "" :err "" :result :ok}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :before #"line #1" :match :all})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "new line
This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
new line
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "This is line #9" :before #"line #1" :match :all})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #9
This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"unmatched" :line "new line" :before #"line #1$"})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "new line
This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"is line" :line "new line" :match :first})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "new line
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"is line" :line "new line" :match :last})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
new line
")))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :present {:path tmp :regexp #"is line" :line "new line" :match :all})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "new line
new line
new line
new line
new line
new line
new line
new line
new line
new line
")))


     )))


(deftest line-in-file-absent-test
  (testing "line-in-file :absent by line-num"
    (transport/ssh
     test-config/localhost
     (try
       (line-in-file :absent {:path "test/files/line-in-file/missing-file" :line-num 3})
       (catch clojure.lang.ExceptionInfo e
         (is (= (ex-data e) {:exit 1 :out "" :err "File not found." :result :failed}))))

     (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
       (is (=
            (line-in-file :absent {:path tmp :line-num 3})
            {:exit 0 :out "" :err "" :result :changed}))
       (is (= (slurp tmp)
              "This is line #1
This is line #2
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
"))))
      )

  (testing "line-in-file :absent by regexp"
    (transport/ssh
     test-config/localhost
      (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file :absent {:path tmp :regexp #"line #3"})
             {:exit 0 :out "" :err "" :result :changed}))
        (is (= (slurp tmp)
               "This is line #1
This is line #2
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

      (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file :absent {:path tmp :regexp #"unmatched"})
             {:exit 0 :out "" :err "" :result :ok}))
        (is (= (slurp tmp)
               "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
"))))))

(deftest line-in-file-string-match
  (testing "string-match-replace"
    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "This is line #5" :line "line 5"})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2
This is line #3
This is line #4
line 5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "is line" :line "replacement"
                                   :match :last})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
replacement
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "is line" :line "replacement"
                                   :match :first})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "replacement
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "is line" :line "replacement"})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "replacement
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "not found" :line "replacement"
                                   :insert-at :bof})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "replacement
This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "not found" :line "replacement"
                                   :insert-at :eof})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
replacement
")))


    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "is line" :line "replacement"
                                   :match :all})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "replacement
replacement
replacement
replacement
replacement
replacement
replacement
replacement
replacement
replacement
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/regexp-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "a $ character" :line "replacement"})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2 and it contains a \\ character
This is line #3
This is line #4 and it contains a ' character
This is line #5 and it contains a \" character
This is line #6
This is line #7
This is line #8
This is line #9 and it contains a / character
This is line #10
This is line #11
This is line #12 and it contains a . character
This is line #13 and it contains a * character
This is line #14
replacement
This is line #16
This is line #17
This is line #18 and it contains a | character
This is line #19 and it contains a [ character
This is line #20
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/regexp-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "a . character" :line "replacement"
                                   :match :all})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2 and it contains a \\ character
This is line #3
This is line #4 and it contains a ' character
This is line #5 and it contains a \" character
This is line #6
This is line #7
This is line #8
This is line #9 and it contains a / character
This is line #10
This is line #11
replacement
This is line #13 and it contains a * character
This is line #14
This is line #15 and it contains a $ character
This is line #16
This is line #17
This is line #18 and it contains a | character
This is line #19 and it contains a [ character
This is line #20
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/regexp-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :string-match "a . character" :line "replacement"
                                   :match :all})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2 and it contains a \\ character
This is line #3
This is line #4 and it contains a ' character
This is line #5 and it contains a \" character
This is line #6
This is line #7
This is line #8
This is line #9 and it contains a / character
This is line #10
This is line #11
replacement
This is line #13 and it contains a * character
This is line #14
This is line #15 and it contains a $ character
This is line #16
This is line #17
This is line #18 and it contains a | character
This is line #19 and it contains a [ character
This is line #20
")))

    ))





(deftest line-in-file-line-match
  (testing "line-match-replace"
    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :line-match "This is line #5" :line "line 5"})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2
This is line #3
This is line #4
line 5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :line-match "line #5" :line "line 5"})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
line 5
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (spit tmp "line 1
line 2
line 2
")
      (is (=
           (line-in-file :present {:path tmp :line-match "line 2" :line "replacement"
                                   :match :last})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "line 1
line 2
replacement
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (spit tmp "line 1
line 2
line 2
")
      (is (=
           (line-in-file :present {:path tmp :line-match "line 2" :line "replacement"
                                   :match :first})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "line 1
replacement
line 2
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (spit tmp "line 1
line 2
line 2
")
      (is (=
           (line-in-file :present {:path tmp :line-match "line 2" :line "replacement"
                                   :match :all})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "line 1
replacement
replacement
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :line-match "This is line" :line "replacement"
                                   :insert-at :bof})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "replacement
This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :line-match "This is line" :line "replacement"
                                   :insert-at :eof})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2
This is line #3
This is line #4
This is line #5
This is line #6
This is line #7
This is line #8
This is line #9
This is line #10
replacement
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/regexp-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :line-match "This is line #15 and it contains a $ character" :line "replacement"})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2 and it contains a \\ character
This is line #3
This is line #4 and it contains a ' character
This is line #5 and it contains a \" character
This is line #6
This is line #7
This is line #8
This is line #9 and it contains a / character
This is line #10
This is line #11
This is line #12 and it contains a . character
This is line #13 and it contains a * character
This is line #14
replacement
This is line #16
This is line #17
This is line #18 and it contains a | character
This is line #19 and it contains a [ character
This is line #20
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/regexp-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :line-match "This is line #12 and it contains a . character" :line "replacement"
                                   :match :all})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2 and it contains a \\ character
This is line #3
This is line #4 and it contains a ' character
This is line #5 and it contains a \" character
This is line #6
This is line #7
This is line #8
This is line #9 and it contains a / character
This is line #10
This is line #11
replacement
This is line #13 and it contains a * character
This is line #14
This is line #15 and it contains a $ character
This is line #16
This is line #17
This is line #18 and it contains a | character
This is line #19 and it contains a [ character
This is line #20
")))

    (test-utils/with-temp-files [tmp "test/files/line-in-file/regexp-file.txt"]
      (is (=
           (line-in-file :present {:path tmp :line-match "a . character" :line "replacement"
                                   :match :all})
           {:exit 0 :out "" :err "" :result :changed}))
      (is (= (slurp tmp)
             "This is line #1
This is line #2 and it contains a \\ character
This is line #3
This is line #4 and it contains a ' character
This is line #5 and it contains a \" character
This is line #6
This is line #7
This is line #8
This is line #9 and it contains a / character
This is line #10
This is line #11
This is line #12 and it contains a . character
This is line #13 and it contains a * character
This is line #14
This is line #15 and it contains a $ character
This is line #16
This is line #17
This is line #18 and it contains a | character
This is line #19 and it contains a [ character
This is line #20
replacement
")))

    ))
