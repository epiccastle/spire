(ns test-pod.test-transport
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]))

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.transport :as transport]
         '[pod.epiccastle.spire.facts :as facts])

(def username (or (System/getenv "TEST_USER") (System/getenv "USER")))
(def hostname (or (System/getenv "TEST_HOST") "localhost"))

(defn split-lines [out]
  (string/split out #"\n"))

(defn strip-empty [strings]
  (->> strings
       (filter (comp not empty?))
       (into [])))

(defn connections [search]
  (-> (process ["netstat" "-atp"])
      (process ["grep" search])
      (process ["grep" "ESTABLISHED"])
      (process ["grep" "java"])
      :out
      slurp
      split-lines
      strip-empty))

(defn test-connection [username hostname]
  (let [search (str hostname ":ssh")
        conns (count (connections search))
        session (transport/connect {:username username :hostname hostname :port 22})]
    (assert (= 1 (- (count (connections search)) conns)))
    (transport/disconnect session)
    (assert (= 0 (- (count (connections search)) conns)))))

(test-connection username hostname)
