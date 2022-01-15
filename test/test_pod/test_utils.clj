(ns test-pod.test-utils
  (:require [test-pod.utils :refer [bash]]
            [test-pod.conf :as conf]
            [clojure.string :as string]
            [clojure.java.io :as io]

            [pod.epiccastle.spire.utils :as utils]

            [clojure.test :refer [is deftest]]
            ))

(deftest to-snakecase
  (is (= (utils/to-snakecase "foo Bar baZ")
             "foo-bar-baz")))

(deftest lsb-process
  (is (= (utils/lsb-process
              "Distributor ID:	Ubuntu
Description:	Ubuntu 18.04.5 LTS
Release:	18.04
Codename:	bionic
")
             {:distributor-id "Ubuntu"
              :description "Ubuntu 18.04.5 LTS"
              :release "18.04"
              :codename "bionic"})))

(deftest escape-codes
  (is (= "\033[31m" (utils/escape-code 31)))
  (is (= "\033[31;32;0m") (utils/escape-codes 31 32 0))
  (is (map? utils/colour-map))
  (is (= "\033[34m" (utils/colour :blue)))
  (is (= "\033[7m" (utils/reverse-text true)))
  (is (= "\033[1m" (utils/bold true)))
  (is (= "\033[0m" (utils/reset))))

(deftest byte-sizes
  (is (and utils/kilobyte utils/megabyte utils/gigabyte)))

(deftest speed-string
  (is (= "765 B/s" (utils/speed-string 765)))
  (is (= "9.64 kB/s" (utils/speed-string 9876)))
  (is (= "2.74 MB/s" (utils/speed-string 2873467)))
  (is (= "818.99 GB/s" (utils/speed-string 879384759348))))

(deftest eta-string
  (is (= "21s" (utils/eta-string 21.3)))
  (is (= "49m27s" (utils/eta-string 2967)))
  (is (= "1h39m27s" (utils/eta-string 5967)))
  (is (= "27h46m39s" (utils/eta-string 99999))))

(deftest content-size
  (bash "rm -f /tmp/foo.txt")
  (bash "echo -n foo > /tmp/foo.txt")
  (is (= 3 (utils/content-size (io/file "/tmp/foo.txt"))))
  (is (= 3 (utils/content-size "foo")))
  (is (= 3 (utils/content-size (byte-array 3)))))

(deftest content-display-name
  (bash "rm -f /tmp/foo.txt")
  (bash "echo -n foo > /tmp/foo.txt")
  (is (= "foo.txt" (utils/content-display-name (io/file "/tmp/foo.txt"))))
  (is (= "[String Data]" (utils/content-display-name "foo")))
  (is (= "[Byte Array]" (utils/content-display-name (byte-array 3)))))

(deftest content-recursive?
  (bash "rm -rf /tmp/foo")
  (bash "mkdir /tmp/foo")
  (is (not (utils/content-recursive? (io/file "/tmp/foo.txt"))))
  (is (utils/content-recursive? (io/file "/tmp/foo")))
  (is (not (utils/content-recursive? "foo")))
  (is (not (utils/content-recursive? (byte-array 3)))))

(deftest content-file?
  (bash "rm -rf /tmp/foo")
  (bash "mkdir /tmp/foo")
  (is (utils/content-file? (io/file "/tmp/foo.txt")))
  (is (not (utils/content-file? (io/file "/tmp/foo"))))
  (is (not (utils/content-file? "foo")))
  (is (not (utils/content-file? (byte-array 3)))))

(deftest content-stream
  (bash "rm -f /tmp/foo.txt")
  (bash "echo -n foo > /tmp/foo.txt")
  (is (= java.io.BufferedInputStream (class (utils/content-stream (io/file "/tmp/foo.txt")))))
  (is (= java.io.BufferedInputStream (class (utils/content-stream "foo"))))
  (is (= java.io.BufferedInputStream (class (utils/content-stream (byte-array 3))))))

(deftest terminal
  (is (boolean? (utils/has-terminal?)))
  (is (integer? (utils/get-terminal-width))))

(deftest progress-stats
  (bash "rm -f /tmp/foo.txt")
  (bash "echo -n foo > /tmp/foo.txt")
  (let [now (java.util.Date.)
        f (io/file "/tmp/foo.txt")
        a (->
           (utils/progress-stats f 1 3 0.333 3 7
                                 {:start-time now
                                  :start-bytes 0
                                  :fileset-file-start 0})
           (assoc-in [:context :start-time] :fake)
           (assoc-in [:progress :bytes-per-second] 11))
        b {:context {:start-time :fake
                     :fileset-file-start 0
                     :start-bytes 0}
           :progress {:bytes-per-second 11
                      :file f
                      :total 3
                      :bytes 1
                      :max-filename-length 7
                      :eta 0
                      :frac 1/3}}]
    (is (= a b))))

(deftest progress-bar-from-stats
  (bash "rm -f /tmp/foo.txt")
  (bash "echo -n foo > /tmp/foo.txt")
  (let [f (io/file "/tmp/foo.txt")
        line (utils/progress-bar-from-stats "localhost" 9 7
                                            {:bytes-per-second 11
                                             :file f
                                             :total 3
                                             :bytes 1
                                             :max-filename-length 7
                                             :eta 0
                                             :frac 1/3})]
    (is (string/starts-with? line "localhost foo.txt|=="))
    (is (.contains line "33% 11 B/s eta:0s"))))

(deftest terminal-display
  (is (= "test"
         (utils/strip-colour-codes (str (utils/colour :blue) "test" (utils/reset)))))
  (is (= 4
         (utils/displayed-length (str (utils/colour :blue) "test" (utils/reset)))))
  (is (= "   " (utils/n-spaces 3)))
  (is (= "\033[2Kline") (utils/append-erasure-to-line "line"))
  (is (= 1 (utils/num-terminal-lines "line"))))

#_(deftest embed
  (bash "rm -f /tmp/foo.txt")
  (bash "echo -n foo > /tmp/foo.txt")
  (is (= "foo" (utils/embed "/tmp/foo.txt")))
  (is (string/starts-with?
       (utils/embed-src "test_utils.clj")
       "(ns test-pod.test-utils")))

(deftest make-script
  (is (string/starts-with?
       (utils/make-script "test_utils.clj" {:key1 "val1" :key2 "val2"})
       "key1=\"val1\"\nkey2=\"val2\"\n(ns test-pod.test-utils")))

(deftest re-pattern-to-sed
  (is (= (utils/re-pattern-to-sed #"^\w+\s+\w+$")
         "/^\\w+\\s+\\w+$/")))

(deftest containing-folder
  (bash "rm -rf /tmp/foo")
  (bash "mkdir /tmp/foo")
  (bash "touch /tmp/foo/bar")
  (is (= (utils/containing-folder "/tmp/foo/bar")
         "/tmp/foo")))

(deftest path-escapes
  (is (= (utils/path-escape "file-with-quotes-'-\"!")
         "file-with-quotes-'-\\\"!"))
  (is (= "\\$foo" (utils/var-escape "$foo")))
  (is (= "\"foo\"" (utils/double-quote "foo")))
  (is (= "\"f\\\"o'o\"" (utils/path-quote "f\"o'o")))
  (is (= "file-with-quotes-'-\\\\\\\"!" (utils/string-escape "file-with-quotes-'-\\\"!")))
  (is (= "\"file-with-quotes-'-\\\\\\\"!\"" (utils/string-quote "file-with-quotes-'-\\\"!"))))

(deftest current-file
  (is (string/ends-with?
       (utils/current-file)
       "/test/test_pod/runner.clj")) ;; deftest runs inside runner
  (is (string/ends-with?
       (utils/current-file-parent)
       "/test/test_pod")))
(is (string/ends-with?
     (utils/current-file)
     "/test/test_pod/test_utils.clj")) ;; outside deftest does not

(deftest defmodule
  (is (= (utils/defmodule foo [args] [pipeline args] (println "foo"))
         #'test-pod.test-utils/foo)))

(deftest wrap-report
  (is (= 'do (first (macroexpand-1 '(pod.epiccastle.spire.utils/wrap-report (println "foo") (println "bar")))))))

(deftest changed?
  (is (utils/changed? {:exit 0 :result :changed}))
  (is (not (utils/changed? {:exit 0 :result :ok})))
  (is (not (utils/changed? {:exit 0 :result :failed}))))

(deftest failed?
  (is (utils/failed? (throw (ex-info "exception message" {:result :failed}))))
  (is (not (utils/failed? true))))

(deftest succeeded?
  (is (utils/succeeded? true))
  (is (not (utils/succeeded? (throw (ex-info "exception message" {:result :failed}))))))

(deftest debug
  (is (macroexpand-1 '(utils/debug true))))
