#!/usr/bin/env spire

(ns test-require
  (:require [test-require-import :as t]))

(assert (= t/val 10))
(assert (= (t/square 10) 100))

(def val 5)

(assert (= (load-file "test-require-load-file.clj") 50))
