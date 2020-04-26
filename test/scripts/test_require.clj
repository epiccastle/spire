#!/usr/bin/env spire

(ns test-require
  (:require [test_require_import :as t]))

(assert (= t/val 10))
(assert (= (t/square 10) 100))

(def val 5)

(assert (= (load-file "test_require_load_file.clj") 50))
