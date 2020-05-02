(ns spire.sh-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [spire.sh :as sh]))

(deftest sh-test
  (testing "basic exec"
    (is (= (sh/exec " echo \"foo\" " "" "UTF-8" {})
           {:exit 0
            :out "foo\n"
            :err ""}))
    (is (= (sh/exec "cat" "in\n" "UTF-8" {})
           {:exit 0
            :out "in\n"
            :err ""}))

    (is (= (let [{:keys [channel out-stream err-stream]}
                 (sh/exec " echo foo " "" :stream {})
                 out (io/reader out-stream)
                 err (io/reader err-stream)
                 ]
             [(slurp out) (slurp err)])
           ["foo\n" ""]))

    (is (= (let [{:keys [channel out-stream err-stream]}
                 (sh/exec "cat" "in\n" :stream {})
                 out (io/reader out-stream)
                 err (io/reader err-stream)
                 ]
             [(slurp out) (slurp err)])
           ["in\n" ""]))

    (is (= (let [prev-out (java.io.PipedOutputStream.)
                 in-stream (java.io.PipedInputStream. prev-out)

                 {:keys [channel out-stream err-stream]}
                 (sh/exec "cat" in-stream :stream {})
                 out (io/reader out-stream)
                 err (io/reader err-stream)
                 ]
             (.write prev-out (int \i))
             (.write prev-out (int \n))
             (.write prev-out (int \newline))
             (.flush prev-out)
             (.close prev-out)
             ;;(.flush in-stream)
             ;;(Thread/sleep 1000)
             ;;(.close in-stream)
             ;;(.read out)
             [(slurp out) (slurp err)]
             )
           ["in\n" ""]))

    (is (= (let [prev-out (java.io.PipedOutputStream.)
                 in-stream (java.io.PipedInputStream. prev-out)

                 {:keys [channel out-stream err-stream]}
                 (sh/exec "cat" in-stream :stream {})
                 out (io/reader out-stream)
                 err (io/reader err-stream)
                 ]
             (.write prev-out (int \i))
             (Thread/sleep 1000)
             (.write prev-out (int \n))
             (Thread/sleep 1000)
             (.write prev-out (int \newline))
             (Thread/sleep 1000)
             (.flush prev-out)
             (.close prev-out)
             ;;(.flush in-stream)
             ;;(Thread/sleep 1000)
             ;;(.close in-stream)
             ;;(.read out)
             [(slurp out) (slurp err)]
             )
           ["in\n" ""]))
    ))
