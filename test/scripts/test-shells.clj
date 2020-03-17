#!/usr/bin/env spire

(def username (second *command-line-args*))
(def host (nth *command-line-args* 2))

(assert (and username host)
        "No username and hostname/ip specified. Pass in username as first argument, and host as second argument.")

(def shells ["fish" "bash" "dash" "csh" "sash" "yash" "zsh" "ksh" "tcsh"])

(def user-conf {:username username :hostname host :key :user})
(def root-conf {:username "root" :hostname host :key :root})

(ssh root-conf
     (apt :install shells)

     (try
       (doall
        (for [sh shells]
          (do
            (user :present {:name username :shell (get-fact [:paths (keyword sh)])})
            (ssh user-conf
                 (let [{:keys [command]} (get-fact [:shell])]
                   ;; sash is reported as `sh`?
                   (assert (= (get {"sh" "sash"} command command) sh)))))))
       (finally
         (user :present {:name username :shell "/bin/bash"}))))
