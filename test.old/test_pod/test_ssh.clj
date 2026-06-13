(ns test-pod.test-ssh
  (:require [clojure.test :refer [is deftest]]

            [pod.epiccastle.spire.transport :as transport]
            [pod.epiccastle.spire.state :as state]
            [pod.epiccastle.spire.ssh :as ssh]
            ))

(deftest ssh-exec-proc
  (transport/ssh
   "localhost"
   (let [{:keys [channel out in err]}
         (ssh/ssh-exec-proc
          state/connection
          "echo -n foo"
          {}
          )]
     (is (= (mapv (fn [_] (.read out)) (range 5))
                [102 111 111 -1 -1])))

   (let [{:keys [channel out in err]}
         (ssh/ssh-exec-proc
          state/connection
          "echo -n foo"
          {}
          )]

     (let [oa (byte-array 10)]
       (is (= 3 (.read out oa 3 10)))
       (is (= (seq oa)
                  '(0 0 0 102 111 111 0 0 0 0))))

     (let [oa (byte-array 10)]
       (is (= -1 (.read out oa 3 10)))
       (is (= (seq oa)
                  '(0 0 0 0 0 0 0 0 0 0)))))

   (let [{:keys [channel out in err]}
         (ssh/ssh-exec-proc
          state/connection
          "cat"
          {})]
     (doseq [n (range 5)] (.write in n))
     (.close in)
     (is (= (doall (for [n (range 10)] (.read out)))
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
       (is (= 256 (.read out ba 0 256)))
       (is (= (take 256 (seq ba))
                  (range -128 128)))))

   ))

 ;;
 ;; ssh/ssh-exec
 ;;

(deftest ssh-exec*
  (transport/ssh
   "localhost"
   (is (= (ssh/ssh-exec* state/connection "echo foo" "" "UTF-8" {})
              {:exit 0 :out "foo\n" :err ""}))

   (is (= (ssh/ssh-exec* state/connection "echo foo 1>&2" "" "UTF-8" {})
              {:exit 0 :out "" :err "foo\n"}))

   (let [{:keys [channel out-stream err-stream]}
         (ssh/ssh-exec state/connection "echo -n foo" "" :stream {})
         ]
     (is (= (mapv (fn [_] (.read out-stream)) (range 5))
                [102 111 111 -1 -1])))))

(deftest parsers
  (is (= (ssh/parse-host-string "user@localhost")
             {:username "user", :hostname "localhost", :port 22}))

  (is (= (ssh/parse-host-string "some.domain:2200")
             {:hostname "some.domain", :port 2200}))

  (is (= (ssh/host-config-to-string {:username "user", :hostname "localhost", :port 22})
             "user@localhost"))

  (is (= (ssh/host-config-to-connection-key
              {:username "user"
               :hostname "localhost"
               :port 22
               :public-key "blah"
               :agent-forwarding true})
             {:username "user", :hostname "localhost", :port 22}))

  (is (= (ssh/fill-in-host-description-defaults {:host-string "user@localhost"})
             {:host-string "user@localhost"
              :key "user@localhost"
              :username "user"
              :hostname "localhost"
              :port 22}))

  (is (= (ssh/host-description-to-host-config "user@localhost")
             {:host-string "user@localhost"
              :key "user@localhost"
              :username "user"
              :hostname "localhost"
              :port 22}))

  (is (= (ssh/host-description-to-host-config {:username "user" :hostname "localhost"})
             {:host-string "user@localhost"
              :key "user@localhost"
              :username "user"
              :hostname "localhost"})))
