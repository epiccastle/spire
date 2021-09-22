(ns spire.sudo
  (:import [java.io PipedInputStream PipedOutputStream]))

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

(defn prefix-sudo-stdin
  "given a stdin string or PipedInputStream intended to be directed to
  a processes stdin, prefix it with  the password and a carridge return.
  return the new string or the new PipedInputStream."
  [{:keys [password required?]} stdin]
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
