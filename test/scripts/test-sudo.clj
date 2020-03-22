#!/usr/bin/env spire

(def username (second *command-line-args*))
(def host (nth *command-line-args* 2))

(assert (and username host)
        "No username and hostname/ip specified. Pass in username as first argument, and host as second argument.")

(def shells
  ["fish" "bash" "dash" "csh" "sash" "yash" "zsh" "ksh" "tcsh"]
  #_["fish" "bash" "dash" "csh" "sash" "yash" "zsh" "ksh" "tcsh"])

(def user-conf {:username username :hostname host :key :user})
(def root-conf {:username "root" :hostname host :key :root})

(ssh root-conf
     (apt :install shells)

     (try
       (doall
        (for [sh shells]
          (do
            (user :present {:name "root" :shell (get-fact [:paths (keyword sh)])})
            (user :present {:name username :shell (get-fact [:paths (keyword sh)])})
            (debug [:shell sh])
            (ssh user-conf
                 ;; (debug (get-fact [:shell]))

                 ;; shell
                 (assert (= username (first (:out-lines (shell {:cmd "whoami"})))))
                 (assert (= "root" (first (:out-lines (sudo (shell {:cmd "whoami"}))))))

                 ;; apt
                 (assert (failed? (apt :install "bash")))
                 (assert (not (failed? (sudo (apt :install "bash")))))

                 ;; apt-repo
                 (assert (failed? (apt-repo :present {:repo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" :filename "spire-test"})))
                 (sudo
                  (assert (not (failed? (apt-repo :present {:repo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main" :filename "spire-test"}))))
                  (assert (not (failed? (apt-repo :absent {:repo "deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main"})))))

                 ;; authorized-keys
                 (assert (failed? (authorized-keys :present {:user "root" :key "my-fake-key"})))
                 (sudo
                  (assert (not (failed? (authorized-keys :present {:user "root" :key "my-fake-key"}))))
                  (assert (= 1 (count (filter #(= "my-fake-key" %) (:out-lines (authorized-keys :get {:user "root"}))))))
                  (assert (not (failed? (authorized-keys :absent {:user "root" :key "my-fake-key"})))))

                 ;; get-file
                 (assert (failed? (get-file "/root/.bash_history")))
                 (sudo (assert (not (failed? (get-file "/root/.bash_history")))))

                 ;; group
                 (assert (failed? (group :present {:name "spire-test"})))
                 (sudo (assert (not (failed? (group :present {:name "spire-test"})))))
                 (assert (failed? (group :absent {:name "spire-test"})))
                 (sudo (assert (not (failed? (group :absent {:name "spire-test"})))))

                 ))))
       (finally
         (user :present {:name "root" :shell "/bin/bash"})
         (user :present {:name username :shell "/bin/bash"}))))
