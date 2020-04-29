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




  )
