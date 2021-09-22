(ns test-pod.test-utils
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            ))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.utils :as utils])

(def username (or (System/getenv "TEST_USER") (System/getenv "USER")))

(defn run [args]
  (-> args process :out slurp))

(defn run-trim [args]
  (-> args run string/trim))

(defn run-int [args]
  (-> args run-trim Integer/parseInt))

(defn stat-mode [file]
  ;; gnu stat format. linux only
  (run-trim ["stat" "-c" "%a" file]))

(defn stat-last-modified-time [file]
  (run-int ["stat" "-c" "%Y" file]))

(defn stat-last-access-time [file]
  (run-int ["stat" "-c" "%X" file]))

(defn bash [command]
  (run ["bash" "-c" command]))

(assert (= (utils/to-snakecase "foo Bar baZ")
           "foo-bar-baz"))

(assert (= (utils/lsb-process
            "Distributor ID:	Ubuntu
Description:	Ubuntu 18.04.5 LTS
Release:	18.04
Codename:	bionic
")
           {:distributor-id "Ubuntu"
            :description "Ubuntu 18.04.5 LTS"
            :release "18.04"
            :codename "bionic"}))

(assert (= "\033[31m" (utils/escape-code 31)))
(assert (= "\033[31;32;0m") (utils/escape-codes 31 32 0))
(assert (map? utils/colour-map))
(assert (= "\033[34m" (utils/colour :blue)))
(assert (= "\033[7m" (utils/reverse-text true)))
(assert (= "\033[1m" (utils/bold true)))
(assert (= "\033[0m" (utils/reset)))

(assert (and utils/kilobyte utils/megabyte utils/gigabyte))

(assert (= "765 B/s" (utils/speed-string 765)))
(assert (= "9.64 kB/s" (utils/speed-string 9876)))
(assert (= "2.74 MB/s" (utils/speed-string 2873467)))
(assert (= "818.99 GB/s" (utils/speed-string 879384759348)))

(assert (= "21s" (utils/eta-string 21.3)))
(assert (= "49m27s" (utils/eta-string 2967)))
(assert (= "1h39m27s" (utils/eta-string 5967)))
(assert (= "27h46m39s" (utils/eta-string 99999)))

(bash "echo -n foo > /tmp/foo.txt")
(assert (= 3 (utils/content-size (io/file "/tmp/foo.txt"))))
(assert (= 3 (utils/content-size "foo")))
(assert (= 3 (utils/content-size (byte-array 3))))

(assert (= "foo.txt" (utils/content-display-name (io/file "/tmp/foo.txt"))))
(assert (= "[String Data]" (utils/content-display-name "foo")))
(assert (= "[Byte Array]" (utils/content-display-name (byte-array 3))))

(bash "rm -rf /tmp/foo")
(bash "mkdir /tmp/foo")
(assert (not (utils/content-recursive? (io/file "/tmp/foo.txt"))))
(assert (utils/content-recursive? (io/file "/tmp/foo")))
(assert (not (utils/content-recursive? "foo")))
(assert (not (utils/content-recursive? (byte-array 3))))

(assert (utils/content-file? (io/file "/tmp/foo.txt")))
(assert (not (utils/content-file? (io/file "/tmp/foo"))))
(assert (not (utils/content-file? "foo")))
(assert (not (utils/content-file? (byte-array 3))))

(assert (= java.io.BufferedInputStream (class (utils/content-stream (io/file "/tmp/foo.txt")))))
(assert (= java.io.BufferedInputStream (class (utils/content-stream "foo"))))
(assert (= java.io.BufferedInputStream (class (utils/content-stream (byte-array 3)))))

(assert (boolean? (utils/has-terminal?)))
(assert (integer? (utils/get-terminal-width)))

(def now (java.util.Date.))

;; TODO: decouple printing from call
#_(assert (= (utils/progress-bar 45 2984 0.2 {:start-time now :start-bytes 20})
           {:start-time now :start-bytes 20}))
(def f (io/file "/tmp/foo.txt"))
(let [a (->
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
  ;; (prn 'a a)
  ;; (prn 'b b)
  (assert (= a b)))

(let [line (utils/progress-bar-from-stats "localhost" 9 7
                                          {:bytes-per-second 11
                                           :file f
                                           :total 3
                                           :bytes 1
                                           :max-filename-length 7
                                           :eta 0
                                           :frac 1/3})]
  (assert (string/starts-with? line "localhost foo.txt|=="))
  (assert (.contains line "33% 11 B/s eta:0s")))

(assert (= "test"
           (utils/strip-colour-codes (str (utils/colour :blue) "test" (utils/reset)))))
(assert (= 4
           (utils/displayed-length (str (utils/colour :blue) "test" (utils/reset)))))
(assert (= "   " (utils/n-spaces 3)))
(assert (= "\033[2Kline") (utils/append-erasure-to-line "line"))
(assert (= 1 (utils/num-terminal-lines "line")))

(assert (= "foo" (utils/embed "/tmp/foo.txt")))
(assert (string/starts-with?
         (utils/embed-src "test-utils.clj")
         "(ns test-pod.test-utils"))

(assert (string/starts-with?
         (utils/make-script "test-utils.clj" {:key1 "val1" :key2 "val2"})
         "key1=\"val1\"\nkey2=\"val2\"\n(ns test-pod.test-utils"))

(assert (= (utils/re-pattern-to-sed #"^\w+\s+\w+$")
           "/^\\w+\\s+\\w+$/"))

(bash "rm -rf /tmp/foo")
(bash "mkdir /tmp/foo")
(bash "touch /tmp/foo/bar")
(assert (= (utils/containing-folder "/tmp/foo/bar")
           "/tmp/foo"))

(assert (= (utils/path-escape "file-with-quotes-'-\"!")
           "file-with-quotes-'-\\\"!"))
(assert (= "\\$foo" (utils/var-escape "$foo")))
(assert (= "\"foo\"" (utils/double-quote "foo")))
(assert (= "\"f\\\"o'o\"" (utils/path-quote "f\"o'o")))
(assert (= "file-with-quotes-'-\\\\\\\"!" (utils/string-escape "file-with-quotes-'-\\\"!")))
(assert (= "\"file-with-quotes-'-\\\\\\\"!\"" (utils/string-quote "file-with-quotes-'-\\\"!")))

(assert (string/ends-with?
         (utils/current-file)
         "/test/test-pod/test-utils.clj"))
(assert (string/ends-with?
         (utils/current-file-parent)
         "/test/test-pod"))

(assert (= (utils/defmodule foo [args] [pipeline args] (println "foo"))
           #'test-pod.test-utils/foo))

(assert (= 'do (first (macroexpand-1 '(utils/wrap-report (println "foo") (println "bar"))))))

(assert (utils/changed? {:exit 0 :result :changed}))
(assert (not (utils/changed? {:exit 0 :result :ok})))
(assert (not (utils/changed? {:exit 0 :result :failed})))

(assert (utils/failed? (throw (ex-info "exception message" {:result :failed}))))
(assert (not (utils/failed? true)))

(assert (utils/succeeded? true))
(assert (not (utils/succeeded? (throw (ex-info "exception message" {:result :failed})))))

(assert (macroexpand-1 '(utils/debug true)))
