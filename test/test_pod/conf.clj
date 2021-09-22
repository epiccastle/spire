(ns test-pod.conf)

(def username (or (System/getenv "TEST_USER") (System/getenv "USER")))
