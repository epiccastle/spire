(ns test-pod.test-sh
  (:require
            [clojure.test :refer [is deftest]]

            [pod.epiccastle.spire.sh :as sh]
            ))


(deftest proc
  (let [result (sh/proc ["echo" "foo"])]
    (is (map? result))
    (is (= (into #{} (keys result))
               #{:out-stream :out-reader :in-stream :in-writer :err-stream :err-reader :process}))))

(deftest proc-string-feed
  (let [process (sh/proc ["cat"])]
    (sh/feed-from-string process "foo")
    (.close ^java.io.OutputStream (:in-stream process))
    (is (= "foo" (slurp (:out-reader process))))))

(deftest exec
  (is (= (sh/exec "echo -n foo" "" "UTF-8" {})
             {:exit 0, :err "", :out "foo"}))

  (is (= (sh/exec "cat" "foo" "UTF-8" {})
             {:exit 0, :err "", :out "foo"}))

  (->> (sh/exec "echo -n foo" "" :stream {})
       keys
       (into #{})
       (= #{:channel :err-stream :out-stream})
       is)

  (->> (sh/exec "echo -n foo" "" :bytes {})
       (= '{:exit 0, :err (), :out (102 111 111)})
       is)

  (let [{:keys [out-stream]} (sh/exec "echo -n foo" "" :stream {})]
    (is (= (mapv (fn [_] (.read out-stream)) (range 5))
               [102 111 111 -1 -1]))))
