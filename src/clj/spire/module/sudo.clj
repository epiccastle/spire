(ns spire.module.sudo
  (:require [spire.state :as state]
            [spire.ssh :as ssh]
            [spire.context :as context]
            [spire.facts :as facts]
            [clojure.string :as string])
  (:import [java.io PipedInputStream PipedOutputStream]))

(defonce passwords (atom {}))

(defn make-sudo-command [{:keys [username group uid gid]} prompt command]
  (let [user-flags (cond
                     username (format "-u '%s'" username)
                     uid (format "-u '#%d'" uid)
                     :else "")
        group-flags (cond
                      group (format "-g '%s'" group)
                      gid (format "-g '#%d'" gid)
                      :else "")]
    (format "sudo -S -p '%s' %s %s %s" prompt user-flags group-flags command)))

(defn copy-string-into-byte-array [s byte-arr off]
  (let [len (count s)
        char-array (byte-array (map #(int (.charAt s %)) (range len)))]
    (System/arraycopy char-array 0 byte-arr off len)))

(defn prefix-sudo-stdin [{:keys [password required?]} stdin]
  (if required?
    (cond
      (string? stdin)
      (str password "\n" stdin)

      (= PipedInputStream (type stdin))
      (let [prefix (atom (if password (str password "\n") ""))]
        (proxy [PipedInputStream] []
          (available [this]
            (+ (count @prefix)
               (.available stdin)))
          (close [this]
            (.close stdin))
          (read
            ([this]
             (let [[old remain] (swap-vals! prefix
                                            (fn [s] (if (empty? s) s (subs s 1))))]
               (if (empty? old)
                 (.read stdin)
                 (int (.charAt old 0)))))
            ([this byte-arr off len]
             (if (zero? len)
               0
               ;; transfer to buffer with prefix
               (let [[old remain] (swap-vals! prefix
                                              (fn [s] (if (< len (count s))
                                                        (subs s len)
                                                        "")))
                     chars-to-prefix? (not (empty? old))
                     taken-num (min len (count old))
                     taken (subs old 0 taken-num)
                     remain? (not (empty remain))]

                 (cond
                   (and chars-to-prefix?
                        (= taken-num len))
                   (do
                     (copy-string-into-byte-array taken byte-arr off)
                     len)

                   (and chars-to-prefix?
                        (< taken-num len))
                   (do
                     (copy-string-into-byte-array taken byte-arr off)
                     (let [copied (.read stdin byte-arr (+ off taken-num) (- len taken-num))]
                       (if (= -1 copied)
                         taken-num
                         (+ taken-num copied))))

                   (not chars-to-prefix?)
                   (.read stdin byte-arr off len)

                   :else
                   -1)))))
          (receive [this b]
            (.receive stdin b))))

      :else
      (assert false (str "Unknown stdin format passed to sudo: " (type stdin))))

    stdin))

(defn requires-password?
  "tests the remote calling of sudo to determine if a password is
  required to change user, or if it is passwordless. The result of
  this alters the way subsequent calls to sudo are initiated."
  [opts]
  (let [
        {:keys [exec-fn exec]} (state/get-shell-context)
        session (state/get-connection)
        cmd (make-sudo-command opts "password required" "id")
        {:keys [out err exit]}
        (if (= :local exec)
          (exec-fn nil "sh" cmd "UTF-8" {})
          (exec-fn session cmd "" "UTF-8" {}))]
    (cond
      (and (= 1 exit) (string/starts-with? err "password required") (= "" out))
      true

      (and (= 0 exit) (string/starts-with? out "uid="))
      false

      :else
      (assert
       false
       (format "Unknown response from sudo test in requires-password? exit: %d err: %s out: %s"
               exit (prn-str err) (prn-str out))))))

(defn sudo-id
  "actually escallates privileges using sudo and calls the system
  command `id`. This both tests if the password (if needed) is correct,
  and gathers user/group data for the escallated session that is then
  used to update system facts while in the body of the sudo macro."
  [opts]
  (let [{:keys [exec-fn exec]} (state/get-shell-context)
        session (state/get-connection)
        cmd (make-sudo-command opts "" "id")
        {:keys [err out exit]}
        (if (= :local exec)
          (exec-fn nil "sh" (prefix-sudo-stdin opts cmd) "UTF-8" {})
          (exec-fn session cmd (prefix-sudo-stdin opts "") "UTF-8" {}))]
    (cond
      (and (= 1 exit) (.contains err "incorrect password") (= "" out))
      (throw (ex-info "sudo: incorrect password" {:module :sudo :cause :incorrect-password}))

      (zero? exit)
      (facts/update-facts-user! (string/split-lines out))

      :else
      (assert
       false
       (format "Unknown response from sudo id in sudo-id exit: %d err: %s out: %s"
               exit (prn-str err) (prn-str out))))))

(defmacro sudo-user [conf & body]
  `(let [conf# ~conf
         required?# (requires-password? conf#)
         host-config# (state/get-host-config)
         store-key# (select-keys host-config# [:username :hostname :port])
         stored-password# (get @passwords store-key#)
         password# (get conf# :password stored-password#)
         full-conf# (assoc conf# :password password# :required? required?#)]

     ;; TODO: if password is required and not specified, prompt for it at the
     ;; terminal, and block thread here until its available.
     (assert
      (or (not required?#) (not (nil? password#)))
      "sudo password is required but not specified")

     (swap! passwords assoc store-key# password#)

     (let [original-facts# (facts/get-fact)]
       (sudo-id full-conf#)

       (context/binding* [state/shell-context
                          {:priveleges :sudo
                           :exec (:exec (context/deref* state/shell-context))
                           :exec-fn (:exec-fn (context/deref* state/shell-context))
                           :shell-fn (partial make-sudo-command full-conf# "")
                           :stdin-fn (partial prefix-sudo-stdin full-conf#)}]
                         (let [result# (do ~@body)]
                           (facts/replace-facts-user! (:user original-facts#))
                           result#)))))

(defmacro sudo [& body]
  `(sudo-user {} ~@body))
