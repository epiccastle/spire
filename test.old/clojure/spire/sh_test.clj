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

    (let [{:keys [channel out-stream err-stream]}
          (sh/exec " echo foo " "" :stream {})
          out (io/reader out-stream)
          err (io/reader err-stream)
          ]
      (is (= (slurp out) "foo\n"))
      (is (= (slurp err) ""))
      (is (= 0 (.waitFor channel)))
      )


    (let [{:keys [channel out-stream err-stream]}
          (sh/exec "cat" "in\n" :stream {})
          out (io/reader out-stream)
          err (io/reader err-stream)
          ]
      (is (= (slurp out) "in\n"))
      (is (= (slurp err) ""))
      (is (= 0 (.waitFor channel)))
      )


    (let [prev-out (java.io.PipedOutputStream.)
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
      (is (= (slurp out) "in\n"))
      (is (= (slurp err) ""))
      (is (= 0 (.waitFor channel)))
      )

    (let [prev-out (java.io.PipedOutputStream.)
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
      (is (= (slurp out) "in\n"))
      (is (= (slurp err) ""))
      (is (= 0 (.waitFor channel))))

    (let [data (apply str (for [n (range 100)] (slurp "project.clj")))]
      (let [prev-out (java.io.PipedOutputStream.)
            in-stream (java.io.PipedInputStream. prev-out)

            {:keys [channel out-stream err-stream]}
            (sh/exec "cat" in-stream :stream {})
            out (io/reader out-stream)
            err (io/reader err-stream)
            ]
        (future (.write prev-out (.getBytes data) 0 (count (.getBytes ^String data)))
                (.flush prev-out)
                (.close prev-out))
        (is (= (slurp out) data))
        (is (= (slurp err) ""))
        (is (= 0 (.waitFor channel)))))


    ;; bytes
    (let [{:keys [channel out err]}
          (sh/exec " echo foo " "" :bytes {})

          out-check (.getBytes "foo\n")
          err-check(.getBytes "")]
      (is (= (count out) (count out-check)))
      (is (= (seq out) (seq out-check)))
      (is (= (count err) (count err-check)))
      (is (= (seq err) (seq err-check))))

    (let [{:keys [channel out err]}
          (sh/exec "cat" "in\n" :bytes {})

          out-check (.getBytes "in\n")
          err-check(.getBytes "")]
      (is (= (count out) (count out-check)))
      (is (= (seq out) (seq out-check)))
      (is (= (count err) (count err-check)))
      (is (= (seq err) (seq err-check))))

    (let [data (apply str (for [n (range 100)] (slurp "project.clj")))]
      (let [prev-out (java.io.PipedOutputStream.)
            in-stream (java.io.PipedInputStream. prev-out)

            ;; have to trigger future feed first, or else sh/exec will block
            ;; building its outputs
            fut (future (.write prev-out (.getBytes data) 0 (count (.getBytes ^String data)))
                        (.flush prev-out)
                        (.close prev-out))

            {:keys [exit out err]}
            (sh/exec "cat" in-stream :bytes {})

            out-check (.getBytes data)
            err-check(.getBytes "")
            ]
        (is (= (count out) (count out-check)))
        (is (= (seq out) (seq out-check)))
        (is (= (count err) (count err-check)))
        (is (= (seq err) (seq err-check)))

        (is (= 0 exit))))))

(deftest chat-test
  (testing "chatting back and forth"
    (let [prev-out (java.io.PipedOutputStream.)
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

      (is (= "in" (.readLine out)))

      (.write prev-out (int \i))
      (.write prev-out (int \2))
      (.write prev-out (int \newline))
      (.flush prev-out)

      (is (= "i2" (.readLine out)))

      (.close prev-out)
      (is (= (slurp err) ""))
      (is (= 0 (.waitFor channel))))))
