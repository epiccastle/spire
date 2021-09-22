(ns test-pod.utils
  (:require [babashka.process :refer [process]]
            [clojure.string :as string]))

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
