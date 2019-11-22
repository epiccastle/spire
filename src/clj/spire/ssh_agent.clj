(ns spire.ssh-agent)

(def codes {:ssh-agent-failure 5
            :request-identities 11
            :identities-answer 12
            :sign-request 13
            :sign-response 14})

(def code->keyword
  (->> codes
       (map (comp vec reverse))
       (into {})))

(defn pack-byte [n]
  [(bit-and n 0xff)])

(defn pack-int [n]
  [(-> n (bit-shift-right 24) (bit-and 0xff))
   (-> n (bit-shift-right 16) (bit-and 0xff))
   (-> n (bit-shift-right 8) (bit-and 0xff))
   (-> n (bit-and 0xff))])

(defn pack-data [data]
  (let [len (count data)]
    (concat
     (pack-int len)
     data)))

(defn unpack-int [[b1 b2 b3 b4]]
  (bit-or
   (-> b1 (bit-and 0xff) (bit-shift-left 24))
   (-> b2 (bit-and 0xff) (bit-shift-left 16))
   (-> b3 (bit-and 0xff) (bit-shift-left 8))
   (-> b4 (bit-and 0xff))))

#_ (unpack-int (pack-int 123456789))

(defn decode-string
  "returns (byte-stream remaining-data)"
  [data]
  (let [[v data] (split-at 4 data)]
    (split-at (unpack-int v) data)))

(defn read-identity
  "returns [[byte-stream comment] remaining-data]"
  [data]
  (let [[byte-stream data] (decode-string data)
        [comment data] (decode-string data)]
    [[byte-stream (apply str (map char comment))] data]))

(defn decode-identities
  "returns vector of identities. Each identity is [byte-seq comment-string]"
  [data]
  (let [[val data] (split-at 4 data)
        num-identities (unpack-int val)]
    (loop [n num-identities
           data data
           identities []]
      (let [[id data] (read-identity data)]
        (if (pos? (dec n))
          (recur (dec n) data (conj identities id))

          (do
            (assert (empty? data) "data should be empty now")
            (conj identities id)))))))

(defn send-query [sock query-data]
  (let [query (concat (pack-int (count query-data)) query-data)
        qarr (byte-array query)
        n (count query)]
    (SpireUtils/ssh-auth-socket-write sock qarr n)))

(defn request-identities [sock]
  ;; send query
  (send-query sock (pack-byte (codes :request-identities)))

  ;; read response
  (let [read (byte-array 4)]
    (SpireUtils/ssh-auth-socket-read sock read 4)
    (let [size (unpack-int read)
          read (byte-array size)]
      (SpireUtils/ssh-auth-socket-read sock read size)
      (case (code->keyword (first read))
        :identities-answer
        (decode-identities (drop 1 read))

        :ssh-agent-failure
        (throw (ex-info "ssh-agent failure" {}))

        (throw (ex-info "improper response code from ssh-agent" {:code (first read)}))))))


(defn sign-request [sock blob data]
  ;; write request query
  (send-query
   sock
   (concat
    (pack-byte (codes :sign-request))
    (pack-data blob)
    (pack-data data)
    (pack-int 0)))

  ;; read response
  (let [read (byte-array 4)]
    (SpireUtils/ssh-auth-socket-read sock read 4)
    (let [size (unpack-int read)
          read (byte-array size)]
      (SpireUtils/ssh-auth-socket-read sock read size)
      (case (code->keyword (first read))
        :sign-response
        (let [[blob data] (decode-string (drop 1 read))]
          (assert (empty? data) "trailing data should not exist")
          blob)

        :ssh-agent-failure
        (throw (ex-info "ssh-agent failure" {:code (first read)
                                             :reason "couldn't access key"}))

        (throw (ex-info "improper response code from ssh-agent" {:code (first read)}))))))

(defn open-auth-socket []
  (when-let [ssh-auth-sock (System/getenv "SSH_AUTH_SOCK")]
    (SpireUtils/ssh-open-auth-socket ssh-auth-sock)))

(defn close-auth-socket [sock]
  (SpireUtils/ssh-close-auth-socket sock))
