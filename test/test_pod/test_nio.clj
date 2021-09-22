(ns test-pod.test-nio
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            ))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.nio :as nio])

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

(assert (= "../bar" (nio/relativise "/path/to/foo" "/path/to/bar")))
(assert (< 1599000000 (nio/last-access-time ".")))
(assert (< 1599000000 (nio/last-modified-time ".")))
(assert (<= 0 (nio/file-mode ".") 0777))
(assert (= 3 (count (nio/mode->permissions 0700))))

(process ["rm" "-rf" "/tmp/foo"])
(nio/create-file "/tmp/foo" 0666)
;; assume umask 022
(assert (= "644" (stat-mode "/tmp/foo")))
(nio/set-file-mode "/tmp/foo" 0777)
(assert (= "777" (stat-mode "/tmp/foo")))

(assert (= "2020-09-01 22:40:00.000000000 +0000" (nio/timestamp->touch 1599000000)))
(assert (= "202009012240.00" (nio/timestamp->touch-bsd 1599000000)))

(nio/set-last-modified-time "/tmp/foo" 1599000000)
(assert (= 1599000000 (stat-last-modified-time "/tmp/foo")))

(nio/set-last-access-time "/tmp/foo" 1599000000)
(assert (= 1599000000 (stat-last-access-time "/tmp/foo")))

(nio/set-last-modified-and-access-time "/tmp/foo" 1599000001 1599000002)
(assert (= 1599000001 (stat-last-modified-time "/tmp/foo")))
(assert (= 1599000002 (stat-last-access-time "/tmp/foo")))

(assert (nio/idem-set-last-access-time "/tmp/foo" 1599000000))
(assert (= 1599000000 (stat-last-access-time "/tmp/foo")))
(assert (not (nio/idem-set-last-access-time "/tmp/foo" 1599000000)))
(assert (= 1599000000 (stat-last-access-time "/tmp/foo")))

(assert (nio/idem-set-last-modified-time "/tmp/foo" 1599000000))
(assert (= 1599000000 (stat-last-modified-time "/tmp/foo")))
(assert (not (nio/idem-set-last-modified-time "/tmp/foo" 1599000000)))
(assert (= 1599000000 (stat-last-modified-time "/tmp/foo")))

(assert (nio/set-owner "/tmp/foo" username))
(def user-id (run-int ["id" "-u"]))
(assert (nio/set-owner "/tmp/foo" user-id))

(nio/set-owner "/tmp/foo" username)
(assert (not (nio/idem-set-owner "/tmp/foo" username)))
(nio/set-owner "/tmp/foo" user-id)
(assert (not (nio/idem-set-owner "/tmp/foo" user-id)))

(def group-id (run-int ["id" "-g"]))
(def group-name (run-trim ["id" "-gn"]))

(assert (nio/set-group "/tmp/foo" group-name))
(assert (not (nio/idem-set-group "/tmp/foo" group-name)))
(assert (nio/set-group "/tmp/foo" group-id))
(assert (not (nio/idem-set-group "/tmp/foo" group-id)))

(assert (nio/idem-set-mode "/tmp/foo" 0400))
(assert (= "400" (stat-mode "/tmp/foo")))
(assert (not (nio/idem-set-mode "/tmp/foo" 0400)))
(assert (= "400" (stat-mode "/tmp/foo")))

(assert (nio/idem-set-mode "/tmp/foo" 0666))
(assert (= "666" (stat-mode "/tmp/foo")))
(assert (not (nio/idem-set-mode "/tmp/foo" 0666)))
(assert (= "666" (stat-mode "/tmp/foo")))

(assert (not (nio/set-attr "/tmp/foo" user-id group-id 0666)))
(assert (nio/set-attr "/tmp/foo" user-id group-id 0400))

(assert (nio/set-attrs {:path "/tmp/foo" :mode 0644}))
(assert (not (nio/set-attrs {:path "/tmp/foo" :mode 0644})))

(run ["rm" "-rf" "/tmp/bar"])
(run ["mkdir" "-p" "/tmp/bar/baz"])
(run ["touch" "/tmp/bar/foo"])
(run ["touch" "/tmp/bar/baz/foo"])
(assert
 (nio/set-attrs-preserve
  {"foo" {:mode 0666 :last-access 1599000000 :last-modified 1599000000}
   "baz" {:mode 0777 :last-access 1599000000 :last-modified 1599000000}
   "baz/foo" {:mode 0666 :last-access 1599000000 :last-modified 1599000000}}
  "/tmp/bar"))

(assert (= 1599000000 (stat-last-modified-time "/tmp/bar/foo")))
(assert (= 1599000000 (stat-last-modified-time "/tmp/bar/baz")))
(assert (= 1599000000 (stat-last-modified-time "/tmp/bar/baz/foo")))
(assert (= 1599000000 (stat-last-access-time "/tmp/bar/foo")))
(assert (= 1599000000 (stat-last-access-time "/tmp/bar/baz")))
(assert (= 1599000000 (stat-last-access-time "/tmp/bar/baz/foo")))
(assert (= "666" (stat-mode "/tmp/bar/foo")))
(assert (= "777" (stat-mode "/tmp/bar/baz")))
(assert (= "666" (stat-mode "/tmp/bar/baz/foo")))
