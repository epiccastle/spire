(ns spire.ssh)

(def codes {:ssh2-agentc-request-identities 11
            :ssh2-agent-identities-answer 12})

(defn pack-byte [n]
  [(bit-and n 0xff)])

(defn pack-int [n]
  [(-> n (bit-shift-right 24) (bit-and 0xff))
   (-> n (bit-shift-right 16) (bit-and 0xff))
   (-> n (bit-shift-right 8) (bit-and 0xff))
   (-> n (bit-and 0xff))])

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
  "returns [[byte-stream id-file-string] remaining-data]"
  [data]
  (let [[byte-stream data] (decode-string data)
        [id-file-string data] (decode-string data)]
    [[byte-stream (apply str (map char id-file-string))] data]))

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
            identities))))))

(defn open-auth-socket []
  (let [ssh-auth-sock (System/getenv "SSH_AUTH_SOCK")
        sock (SpireUtils/ssh-open-auth-socket ssh-auth-sock)]
    (println "FD:" sock)

    (let [query (concat (pack-int 1) (pack-byte (codes :ssh2-agentc-request-identities)))
          qarr (byte-array query)
          n (count query)]
      (println "writing" n "bytes")
      (SpireUtils/ssh-auth-socket-write sock qarr n))

    (let [read (byte-array 4)]
      (println "reading 4 bytes")
      (SpireUtils/ssh-auth-socket-read sock read 4)
      (let [size (unpack-int read)
            read (byte-array size)]
        (println "size is" size)
        (SpireUtils/ssh-auth-socket-read sock read size)
        ;;(println "read:" (map int read))
        (assert (= (first read) (codes :ssh2-agent-identities-answer))
                "improper response code from ssh-agent")

        (let [ids (decode-identities (drop 1 read))]
          (println "identities:")
          (pr ids))))

    (SpireUtils/ssh-close-auth-socket sock)))

#_ (open-auth-socket)

#_ (proxy [com.jcraft.jsch.agentproxy/Connector] []
     (getName []
       (println "Connector::getName"))
     (isAvailable []
       (println "Connector::isAvailable"))
     (query [buffer]
       (println "Connector::query")))

#_ (proxy [com.jcraft.jsch.agentproxy.USocketFactory] []
     (open [path]
       (println "USocketFactory::open")))
