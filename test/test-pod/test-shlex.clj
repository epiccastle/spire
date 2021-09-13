(ns test-pod.test-shlex
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            ))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.shlex :as shlex])

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

(assert (= #{\space \tab \newline \return} shlex/whitespace-chars))
(assert (= shlex/newline-chars #{\newline}))
(assert (= (shlex/read-char "foobar") [\f '(\o \o \b \a \r)]))
(assert (= (shlex/skip-until "foo bar" shlex/whitespace-chars) '(\b \a \r)))
(assert (= (shlex/read-double-quotes "string\" here") ["string" '(\space \h \e \r \e)]))
(assert (= (shlex/read-single-quotes "string' here") ["string" '(\space \h \e \r \e)]))
(assert (= (shlex/read-until-whitespace "string\there") ["string" '(\tab \h \e \r \e)]))
(assert (= (shlex/read-while-whitespace "\t \n \there") ["\t \n \t" '(\h \e \r \e)]))

(assert (= (shlex/parse "ls -alF \"foo bar\"")
           ["ls" "-alF" "foo bar"]))
