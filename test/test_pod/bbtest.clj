;; (ns testbootlegpod
;;   (:require [babashka.pods :as pods]))

;; (if *command-line-args*
;;   (pods/load-pod *command-line-args* {:transport :socket})
;;   (pods/load-pod ["lein" "trampoline" "run"] {:transport :socket}))

;; (require '[pod.epiccastle.spire.transport :as transport]
;;          '[pod.epiccastle.spire.ssh :as ssh]
;;          '[pod.epiccastle.spire.facts :as facts])

;; (comment
;;   (prn ssh/ctrl-c)
;;   (prn ssh/utf-8)
;;   (prn ssh/*piped-stream-buffer-size*)
;;   (println ssh/debug)

;;   (prn (ssh/make-user-info {}))

;;   (prn (ssh/raw-mode-read-line))

;;   (prn (ssh/make-user-info {}))

;;   (prn (ssh/print-flush-ask-yes-no "are you sure?"))

;;   (let [session (transport/connect {:username "crispin" :hostname "localhost" :port 22})]
;;     (prn session)
;;     (transport/disconnect session))

;;   (let [session (transport/open-connection {:username "crispin" :hostname "localhost" :port 22})]
;;     (prn session)
;;     (prn (transport/get-connection {:username "crispin" :hostname "localhost" :port 22}))
;;     (transport/close-connection {:username "crispin" :hostname "localhost" :port 22})))

;; (transport/ssh "crispin@localhost"
;;                (facts/get-fact)
;;                )

(ns testbootlegpod
  (:require [babashka.pods :as pods]))

(require '[babashka.pods :as pods])

(pods/load-pod ["lein" "trampoline" "run"] {:transport :socket})

(require '[pod.epiccastle.spire.transport :as transport]
         '[pod.epiccastle.spire.facts :as facts])

(def facts (transport/ssh "crispin@localhost" (facts/get-fact)))

(clojure.pprint/pprint (facts :system))
