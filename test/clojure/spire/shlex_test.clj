(ns spire.shlex-test
  (:require [clojure.test :refer :all]
            [clojure.string :as string]
            [spire.shlex :refer :all]))

(deftest shlex-test
  (testing "shelx read tests"
    (is (= (read-char "foo bar") [\f (seq "oo bar")]))
    (is (= (skip-until "line1\nline2\n" newline-chars) (seq "line2\n"))))

  (testing "shlex decode double quoted string"
    (is (= ["foo bar" (seq " remain")]
           (read-double-quotes "foo bar\" remain")))
    (is (= ["foo\\bar" (seq " remain")]
           (read-double-quotes "foo\\\\bar\" remain")))
    (is (= ["foo\"bar" (seq " remain")]
           (read-double-quotes "foo\\\"bar\" remain"))))

  (testing "shlex decode single quoted string"
    (is (= ["foo bar" (seq " remain")]
           (read-single-quotes "foo bar' remain")))
    (is (= ["foo\\bar" (seq " remain")]
           (read-single-quotes "foo\\bar' remain")))
    (is (= ["foo\"bar" (seq " remain")]
           (read-single-quotes "foo\"bar' remain")))
    (is (= ["foo" (seq "bar' remain")]
           (read-single-quotes "foo'bar' remain"))))

  (testing "shlex read until whitespace"
    (is (= ["foo" (seq " bar' remain")]
           (read-until-whitespace "foo bar' remain")))
    (is (= ["foo" (seq "\tbar' remain")]
           (read-until-whitespace "foo\tbar' remain")))
    (is (= ["foo" (seq "\nbar' remain")]
           (read-until-whitespace "foo\nbar' remain")))
    (is (= ["foo" (seq "\rbar' remain")]
           (read-until-whitespace "foo\rbar' remain")))
    (is (= ["1234" (seq " remain")]
           (read-until-whitespace "\"1\"2'3'\"4\" remain")))
    (is (= ["123 4 5 6" (seq " remain")]
           (read-until-whitespace "\"1\"2'3 4'\" 5 6\" remain"))))

  (testing "shlex read while whitespace"
    (is (= [" \t \r\n  " (seq "remain")]
           (read-while-whitespace " \t \r\n  remain"))))

  (testing "shlex parse"
    (is (= ["ls" "-alF"]
           (parse "ls -alF")))
    (is (= ["foo" "bar baz" "bar baz" "12345" "a\"b"]
           (parse "foo \"bar baz\" 'bar baz'  '1'\"2\"3\"4\"'5' 'a\"'b" )))
    (is (= ["foo" "'" "\"" "ab\"c"]
           (parse "foo \\' \\\" 'a'\"b\"\\\"\"c\"" )))
    (is (= [] (parse "" )))
    (is (= [] (parse "   " )))
    (is (= ["foo"] (parse " foo  " )))
    (is (= ["foo" "bax"] (parse " foo  \t   bax  " )))
    ))
