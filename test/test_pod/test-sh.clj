(ns test-pod.test-sh
  (:require [babashka.pods :as pods]
            [babashka.process :refer [process check]]
            [clojure.string :as string]
            ))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.sh :as sh])

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

(defn bash [command]
  (run ["bash" "-c" command]))

(let [result (sh/proc ["echo" "foo"])]
  (assert (map? result))
  (assert (= (into #{} (keys result))
             #{:out-stream :out-reader :in-stream :in-writer :err-stream :err-reader :process})))

(let [process (sh/proc ["cat"])]
  (sh/feed-from-string process "foo")
  (.close ^java.io.OutputStream (:in-stream process))
  (assert (= "foo" (slurp (:out-reader process)))))

(assert (= (sh/exec "echo -n foo" "" "UTF-8" {})
           {:exit 0, :err "", :out "foo"}))

(assert (= (sh/exec "cat" "foo" "UTF-8" {})
           {:exit 0, :err "", :out "foo"}))

(->> (sh/exec "echo -n foo" "" :stream {})
     keys
     (into #{})
     (= #{:channel :err-stream :out-stream})
     assert)

(->> (sh/exec "echo -n foo" "" :bytes {})
     (= '{:exit 0, :err (), :out (102 111 111)})
     assert)

(let [{:keys [out-stream]} (sh/exec "echo -n foo" "" :stream {})]
  (assert (= (mapv (fn [_] (.read out-stream)) (range 5))
             [102 111 111 -1 -1])))
