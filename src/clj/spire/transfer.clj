(ns spire.transfer
  (:require [spire.probe :as probe]
            [spire.utils :as utils]
            [spire.ssh-agent :as ssh-agent]
            [spire.ssh :as ssh]
            [spire.known-hosts :as known-hosts]
            [digest :as digest]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as string]
            [edamame.core :as edamame])
  (:import [com.jcraft.jsch JSch]
           [java.io PipedInputStream PipedOutputStream]))

(defn read-until-newline [inputstream]
  (loop [data []]
    (let [c (.read ^PipedInputStream inputstream)]
      (if (= (int \newline) c)
        (str (apply str (map char data)))
        (recur (conj data c))))))

#_ (defn ssh-line [host-string lines]
  (let [
        [username hostname] (ssh/split-host-string host-string)
        agent (JSch.)
        session (ssh/make-session
                 agent hostname
                 {:username username})
        irepo (ssh-agent/make-identity-repository)
        _ (when (System/getenv "SSH_AUTH_SOCK")
            (.setIdentityRepository session irepo))
        user-info (ssh/make-user-info)
        ]
    (doto session
      (.setHostKeyRepository (known-hosts/make-host-key-repository))
      (.setUserInfo user-info)
      (.connect))

    (let [runner (partial ssh/ssh-exec session)
          facts (probe/facts runner)
          compatible? (utils/compatible-arch? facts)
          result (if-not compatible?
                   (binding [*out* *err*]
                     (println (str
                               (utils/colour :red)
                               (format "spire ssh error: %s machine has unsupported architecture/OS (%s %s)"
                                       hostname
                                       (get-in facts [:arch :processor])
                                       (get-in facts [:arch :os]))
                               (utils/colour))))
                   (let [local-spire (utils/which-spire)
                         local-spire-digest (digest/md5 (io/as-file local-spire))
                         spire-dest (str "/tmp/spire-" local-spire-digest)]
                     (utils/push facts host-string runner session local-spire spire-dest)

                     ;;(println "starting")
                     (let [write-stream (PipedOutputStream.)
                           in-stream (PipedInputStream. write-stream)
                           result (runner (format "%s --server" spire-dest)
                                              in-stream
                                              :stream {})
                           out-stream (:out-stream result)
                           err-stream (:err-stream result)
                           ]
                       ;;(println "RESULT" in-stream)
                       ;;(prn result)


                       ;; feed forms, print status, gather responses
                       (loop [forms lines
                              results []]
                         (let [form (first forms)
                               remain (rest forms)]
                           (pr form)
                           (print " ... ")
                           (.flush *out*)

                           (let [;; result (runner (format "%s --server" spire-dest)
                                 ;;                (pr-str form)
                                 ;;                "" {})
                                 ;; result (edamame/parse-string (:out result))

                                 _ (.write write-stream (.getBytes (str (pr-str form) "\n") ssh/utf-8))
                                 _ (.flush write-stream)

                                 result (edamame/parse-string (read-until-newline out-stream))

                                 ;;_ (println "read-result")
                                 ;;_ (prn result)
                                 ;;_ (println)
                                 results (conj results result)
                                 ]
                             (if (zero? (:exit result))
                               (println (str "[" (utils/colour :green) "ok" (utils/colour) "]"))
                               (println (str "[" (utils/colour :red) "failed" (utils/colour) "]")))

                             (if (seq remain)
                               (recur remain results)
                               results)

                             #_(update result :out edamame/parse-string)

                             ))))))]
      (println "closing session")
      (.disconnect session)
      result)))

(defmacro ssh [host-string & body]
  `(ssh-line ~host-string '[~@body]))
