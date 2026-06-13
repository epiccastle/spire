(ns test-pod.test-ssh
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]))

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.transport :as transport]
         '[pod.epiccastle.spire.facts :as facts]
         '[pod.epiccastle.spire.state :as state]
         '[pod.epiccastle.spire.ssh :as ssh]
         )

(def username (or (System/getenv "TEST_USER") (System/getenv "USER")))
(def hostname (or (System/getenv "TEST_HOST") "localhost"))

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

(transport/ssh "localhost"
               (prn state/connection)
               (prn (ssh/ssh-exec-proc
                     state/connection
                     "echo foo"
                     {}
                     ))
               )
