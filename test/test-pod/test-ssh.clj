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

(transport/ssh
 "localhost"
 (let [{:keys [channel out in err]}
       (ssh/ssh-exec-proc
        state/connection
        "echo -n foo"
        {}
        )]
   (assert (= (mapv (fn [_] (.read out)) (range 5))
              [102 111 111 -1 -1])))

 (let [{:keys [channel out in err]}
       (ssh/ssh-exec-proc
        state/connection
        "echo -n foo"
        {}
        )]

   (let [oa (byte-array 10)]
     (assert (= 3 (.read out oa 3 10)))
     (assert (= (seq oa)
                '(0 0 0 102 111 111 0 0 0 0))))

   (let [oa (byte-array 10)]
     (assert (= -1 (.read out oa 3 10)))
     (assert (= (seq oa)
                '(0 0 0 0 0 0 0 0 0 0)))))

 (let [{:keys [channel out in err]}
       (ssh/ssh-exec-proc
        state/connection
        "cat"
        {})]
   (doseq [n (range 5)] (.write in n))
   (.close in)
   (assert (= (doall (for [n (range 10)] (.read out)))
              '(0 1 2 3 4 -1 -1 -1 -1 -1))))

 (let [{:keys [channel out in err]}
       (ssh/ssh-exec-proc
        state/connection
        "cat"
        {})

       input-array (byte-array 256)
       ]
   (doseq [n (range -128 128)] (aset input-array (+ n 128) (byte n)))
   (.write in input-array 0 256)
   (.close in)
   (let [ba (byte-array 300)]
     (assert (= 256 (.read out ba 0 256)))
     (assert (= (take 256 (seq ba))
                (range -128 128)))))

 ;;
 ;; ssh/ssh-exec
 ;;
 (assert (= (ssh/ssh-exec* state/connection "echo foo" "" "UTF-8" {})
            {:exit 0 :out "foo\n" :err ""}))

 (assert (= (ssh/ssh-exec* state/connection "echo foo 1>&2" "" "UTF-8" {})
            {:exit 0 :out "" :err "foo\n"}))

 (let [{:keys [channel out-stream err-stream]}
       (ssh/ssh-exec state/connection "echo -n foo" "" :stream {})
       ]
   (assert (= (mapv (fn [_] (.read out-stream)) (range 5))
              [102 111 111 -1 -1]))))

(assert (= (ssh/parse-host-string "user@localhost")
           {:username "user", :hostname "localhost", :port 22}))

(assert (= (ssh/parse-host-string "some.domain:2200")
           {:hostname "some.domain", :port 2200}))

(assert (= (ssh/host-config-to-string {:username "user", :hostname "localhost", :port 22})
           "user@localhost"))

(assert (= (ssh/host-config-to-connection-key
            {:username "user"
             :hostname "localhost"
             :port 22
             :public-key "blah"
             :agent-forwarding true})
           {:username "user", :hostname "localhost", :port 22}))

(assert (= (ssh/fill-in-host-description-defaults {:host-string "user@localhost"})
           {:host-string "user@localhost"
            :key "user@localhost"
            :username "user"
            :hostname "localhost"
            :port 22}))

(assert (= (ssh/host-description-to-host-config "user@localhost")
           {:host-string "user@localhost"
            :key "user@localhost"
            :username "user"
            :hostname "localhost"
            :port 22}))

(assert (= (ssh/host-description-to-host-config {:username "user" :hostname "localhost"})
           {:host-string "user@localhost"
            :key "user@localhost"
            :username "user"
            :hostname "localhost"}))
