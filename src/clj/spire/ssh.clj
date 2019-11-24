(ns spire.ssh
  (:import [com.jcraft.jsch UserInfo]))

(defn make-user-info []
  (let [state (atom nil)]
    (proxy [UserInfo] []

      (getPassword []
        (print (str "Enter " @state ": "))
        (.flush *out*)
        (SpireUtils/enter-raw-mode 0)
        (let [password (read-line)]
          (SpireUtils/leave-raw-mode 0)
          (println)
          password))

      (promptYesNo [s]
        (print (str s " "))
        (.flush *out*)
        (let [response (read-line)
              first-char (first response)]
          (boolean (#{\y \Y} first-char))))

      (getPassphrase []
        (print (str "Enter " @state ": "))
        (.flush *out*)
        (SpireUtils/enter-raw-mode 0)
        (let [password (read-line)]
          (SpireUtils/leave-raw-mode 0)
          (println)
          password))

      (promptPassphrase [s]
        (reset! state s)
        ;; true decrypt key
        ;; false cancel key decrypt
        true
        )

      (promptPassword [s]
        (reset! state s)
        ;; return true to continue
        ;; false to cancel auth
        true
        )

      (showMessage [s]
        (println s)))))
