(ns spire.ssh
  (:import [com.jcraft.jsch UserInfo]))

;; #define SANE_SET 1		/* Set in `sane' mode. */
;; #define SANE_UNSET 2		/* Unset in `sane' mode. */
;; #define REV 4			/* Can be turned off by prepending `-'. */
;; #define OMIT 8			/* Don't display value. */

;; termios.h
;; #define ECHO	0000010
;; #define ECHOE	0000020
;; #define ECHOK	0000040
;; #define ECHONL	0000100

;; name             /* Name given on command line.  */
;; mode_type type   /* Which structure element to change. */
;; flags            /* Setting and display options.  */
;; bits             /* Bits to set for this mode.  */
;; mask             /* Other bits to turn off for this mode.  */

;; "echo", local, SANE_SET | REV, ECHO, 0

;; display_settings (output_type, &mode, device_name);

;; match_found = set_mode (&mode_info[i], reversed, &mode);
;; require_set_attr = true;



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
        (println "getPassphrase")
        "foo")

      (promptPassphrase [s]
        (println 2 s)
        true)

      (promptPassword [s]
        (reset! state s)
        ;; return true to continue
        ;; false to cancel auth
        true
        )

      (showMessage [s]
        (println 4 s))

      (promptKeyboardInteractive [dest name inst prompt echo]
        (println "promptKeyboardInteractive")
        (println dest)
        (println name)
        (println inst)
        (println prompt)
        (println echo)
        "yes"))))
