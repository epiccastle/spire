(ns test-pod.conf)

(def username (or (System/getenv "TEST_USER") (System/getenv "USER")))
(def hostname (or (System/getenv "TEST_HOST") "localhost"))
