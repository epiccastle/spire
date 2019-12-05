(ns spire.module.line-in-file-test
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


(defmacro ^{:private true} assert-args
  [& pairs]
  `(do (when-not ~(first pairs)
         (throw (IllegalArgumentException.
                 (str (first ~'&form) " requires " ~(second pairs) " in " ~'*ns* ":" (:line (meta ~'&form))))))
       ~(let [more (nnext pairs)]
          (when more
            (list* `assert-args more)))))

(defmacro with-temp-files [bindings & body]
  (assert-args
   (vector? bindings) "a vector for its binding"
   (even? (count bindings)) "an even number of forms in binding vector")
  (let [[sym fname & remain] bindings]
    (if-not remain
      `(let [~sym (create-temp-file ~fname)]
         (try ~@body
              (finally (remove-file ~sym))))
      `(let [~sym (create-temp-file ~fname)]
         (try
           (with-temp-files ~(subvec bindings 2) ~@body)
           (finally (remove-file ~sym)))))))

#_ (macroexpand-1 '(with-temp-files [f "mytemp"] 1 2 3))
#_ (macroexpand-1 '(with-temp-files [f "mytemp" g "mytemp2"] 1 2 3))

(def tmp-dir "/tmp")

(defn rand-string [n]
  (apply str (map (fn [_] (rand-nth "abcdefghijklmnopqrztuvwxyz0123456789")) (range n))))

(defn create-temp-file [src]
  (let [tmp (io/file tmp-dir (str "spire-test-" (rand-string 8)))]
    (io/copy (io/file src) tmp)
    (.getPath tmp)))

#_ (create-temp-file "project.clj")

(defn remove-file [tmp]
  (assert (string/starts-with? tmp "/tmp/"))
  (.delete (io/file tmp)))

#_ (remove-file (create-temp-file "project.clj"))

#_ (with-temp-files [f "project.clj"] (+ 10 20))
#_ (with-temp-files [f "project.clj"] (+ 10 20) (throw (ex-info "foo" {})))

(deftest line-in-file-present-test
  (testing "line-in-file :present by line-num"
    (with-redefs [spire.transport/pipelines test-pipelines
                  spire.ssh/ssh-exec test-ssh-exec]
      (is (=
           (line-in-file* :present {:path "test/files/line-in-file/missing-file" :line-num 3})
           {:exit 1 :out "" :err "File not found." :result :failed}))

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :present {:path tmp :line-num 3 :line "new line 3"})
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
      ))

  (testing "line-in-file :present by regexp"
    (with-redefs [spire.transport/pipelines test-pipelines
                  spire.ssh/ssh-exec test-ssh-exec]
      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :present {:path tmp :regexp #"line #3" :line "new line 3"})
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

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :present {:path tmp :regexp #"line #3" :line "This is line #3"})
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

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :present {:path tmp :regexp #"unmatched" :line "new line"})
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

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :present {:path tmp :regexp #"unmatched" :line "new line" :after #"line #8"})
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

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :present {:path tmp :regexp #"line #9" :line "This is line #9" :after #"line #8"})
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

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :present {:path tmp :regexp #"unmatched" :line "new line" :before #"line #8"})
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

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :present {:path tmp :regexp #"unmatched" :line "new line" :before #"line #1$"})
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


      )))


(deftest line-in-file-absent-test
  (testing "line-in-file :absent by line-num"
    (with-redefs [spire.transport/pipelines test-pipelines
                  spire.ssh/ssh-exec test-ssh-exec]
      (is (=
           (line-in-file* :absent {:path "test/files/line-in-file/missing-file" :line-num 3})
           {:exit 1 :out "" :err "File not found." :result :failed}))

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :absent {:path tmp :line-num 3})
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
      ))

  (testing "line-in-file :absent by regexp"
    (with-redefs [spire.transport/pipelines test-pipelines
                  spire.ssh/ssh-exec test-ssh-exec]
      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :absent {:path tmp :regexp #"line #3"})
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

      (with-temp-files [tmp "test/files/line-in-file/simple-file.txt"]
        (is (=
             (line-in-file* :absent {:path tmp :regexp #"unmatched"})
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


      )))
