(ns spire.ssh)

(def codes {:ssh-agent-failure 5
            :ssh2-agentc-request-identities 11
            :ssh2-agent-identities-answer 12
            :ssh2-agentc-sign-request 13
            :ssh2-agent-sign-response 14})

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

(defn pack-blob [data]
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

(defn ssh2-agentc-request-identities [sock]
  ;; send query
  (let [query (concat (pack-int 1) (pack-byte (codes :ssh2-agentc-request-identities)))
        qarr (byte-array query)
        n (count query)]
    ;;(println "writing" n "bytes")
    (SpireUtils/ssh-auth-socket-write sock qarr n))

  ;; read response
  (let [read (byte-array 4)]
    ;;(println "reading 4 bytes")
    (SpireUtils/ssh-auth-socket-read sock read 4)
    (let [size (unpack-int read)
          read (byte-array size)]
      ;;(println "size is" size)
      (SpireUtils/ssh-auth-socket-read sock read size)
      ;;(println "read:" (map int read))
      (case (code->keyword (first read))
        :ssh2-agent-identities-answer
        (decode-identities (drop 1 read))

        :ssh-agent-failure
        (throw (ex-info "ssh-agent failure" {}))

        (throw (ex-info "improper response code from ssh-agent" {:code (first read)}))))))

(defn ssh2-agentc-sign-request [sock blob data]
  ;; write request query
  (let [query (concat
               (pack-int (+ 1 4 (count blob) 4 (count data) 4))
               (pack-byte (codes :ssh2-agentc-sign-request))
               (pack-blob blob)
               (pack-blob data)
               (pack-int 0))
        qarr (byte-array query)
        n (count query)]
    (SpireUtils/ssh-auth-socket-write sock qarr n))

  ;; read response
  (let [read (byte-array 4)]
    ;;(println "reading 4 bytes")
    (SpireUtils/ssh-auth-socket-read sock read 4)
    (let [size (unpack-int read)
          read (byte-array size)]
      ;;(println "size is" size)
      (SpireUtils/ssh-auth-socket-read sock read size)
      ;;(println "read:" (map int read))
      (case (code->keyword (first read))
        :ssh2-agent-sign-response
        (let [[blob data] (decode-string (drop 1 read))]
          (assert (empty? data) "trailing data should not exist")
          blob)

        :ssh-agent-failure
        (throw (ex-info "ssh-agent failure" {:code (first read)
                                             :reason "couldn't access key"}))

        (throw (ex-info "improper response code from ssh-agent" {:code (first read)}))))))

(defn open-auth-socket []
  (let [ssh-auth-sock (System/getenv "SSH_AUTH_SOCK")
        sock (SpireUtils/ssh-open-auth-socket ssh-auth-sock)]
    (println "FD:" sock)

    (let [identites (ssh2-agentc-request-identities sock)]
      (println "identities:" (count identites))
      (println "sign:" (ssh2-agentc-sign-request
                        sock
                        (get-in identites [3 0])
                        (byte-array (range 32)))))

    (SpireUtils/ssh-close-auth-socket sock)))
