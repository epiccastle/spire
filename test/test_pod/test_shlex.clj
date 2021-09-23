(ns test-pod.test-shlex
  (:require [clojure.test :refer [is deftest]]

            [pod.epiccastle.spire.shlex :as shlex]
            ))

(deftest chars
  (is (= #{\space \tab \newline \return} shlex/whitespace-chars))
  (is (= shlex/newline-chars #{\newline})))

(deftest readers
  (is (= (shlex/read-char "foobar") [\f '(\o \o \b \a \r)]))
  (is (= (shlex/skip-until "foo bar" shlex/whitespace-chars) '(\b \a \r)))
  (is (= (shlex/read-double-quotes "string\" here") ["string" '(\space \h \e \r \e)]))
  (is (= (shlex/read-single-quotes "string' here") ["string" '(\space \h \e \r \e)]))
  (is (= (shlex/read-until-whitespace "string\there") ["string" '(\tab \h \e \r \e)]))
  (is (= (shlex/read-while-whitespace "\t \n \there") ["\t \n \t" '(\h \e \r \e)])))

(deftest parse
  (is (= (shlex/parse "ls -alF \"foo bar\"")
             ["ls" "-alF" "foo bar"])))
