(ns spire.ssh-agent
  (:require [spire.pack :as pack])
  (:import [com.jcraft.jsch Identity IdentityRepository]
           [java.util Vector]))

(def debug false)

(def codes {:ssh-agent-failure 5
            :request-identities 11
            :identities-answer 12
            :sign-request 13
            :sign-response 14})

(def code->keyword
  (->> codes
       (map (comp vec reverse))
       (into {})))

(defn read-identity
  "returns [[byte-stream comment] remaining-data]"
  [data]
  (let [[byte-stream data] (pack/decode-string data)
        [comment data] (pack/decode-string data)]
    [[byte-stream (apply str (map char comment))] data]))

(defn decode-identities
  "returns vector of identities. Each identity is [byte-seq comment-string]"
  [data]
  (let [[val data] (split-at 4 data)
        num-identities (pack/unpack-int val)]
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
  (let [query (concat (pack/pack-int (count query-data)) query-data)
        qarr (byte-array query)
        n (count query)]
    (when debug (prn 'send-query sock query qarr n))
    (SpireUtils/ssh-auth-socket-write sock qarr n)))

(defn request-identities [sock]
  (when debug (prn 'request-identities sock))
  ;; send query
  (when debug (prn 'request-identities 'sending (codes :request-identities) (pack/pack-byte (codes :request-identities))))
  (let [bytes-written (send-query sock (pack/pack-byte (codes :request-identities)))]
    (when debug (prn 'request-identities 'bytes-written bytes-written)))

  ;; read response
  (when debug (prn 'request-identities 'read-response))
  (let [read (byte-array 4)]
    (SpireUtils/ssh-auth-socket-read sock read 4)
    (when debug (prn 'request-identities 'response-read))
    (let [size (pack/unpack-int read)
          read (byte-array size)]
      (when debug (prn 'request-identities 'reading size 'bytes))
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
    (pack/pack-byte (codes :sign-request))
    (pack/pack-data blob)
    (pack/pack-data data)
    (pack/pack-int 0)))

  ;; read response
  (let [read (byte-array 4)]
    (SpireUtils/ssh-auth-socket-read sock read 4)
    (let [size (pack/unpack-int read)
          read (byte-array size)]
      (SpireUtils/ssh-auth-socket-read sock read size)
      (case (code->keyword (first read))
        :sign-response
        (let [[blob data] (pack/decode-string (drop 1 read))]
          (assert (empty? data) "trailing data should not exist")
          blob)

        :ssh-agent-failure
        (throw (ex-info "ssh-agent failure" {:code (first read)
                                             :reason "couldn't access key"}))

        (throw (ex-info "improper response code from ssh-agent" {:code (first read)}))))))

(defn open-auth-socket []
  (when debug (prn 'open-auth-socket (System/getenv "SSH_AUTH_SOCK")))
  (when-let [ssh-auth-sock (System/getenv "SSH_AUTH_SOCK")]
    (let [result (SpireUtils/ssh-open-auth-socket ssh-auth-sock)]
      (when debug (prn 'open-auth-socket 'returning result))
      result)))

(defn close-auth-socket [sock]
  (when debug (prn 'close-auth-socket sock))
  (SpireUtils/ssh-close-auth-socket sock))

(defn make-identity [[blob comment]]
  (proxy [Identity] []
    (getPublicKeyBlob []
      (when debug (prn 'make-identity 'getPublicKeyBlob))
      (byte-array blob))
    (getSignature [data]
      (when debug (prn 'make-identity 'getSignature data))
      (let [sock (open-auth-socket)
            signature (sign-request sock blob data)]
        (close-auth-socket sock)
        (byte-array signature)))
    (getName []
      (when debug (prn 'make-identity 'getName))
      comment)
    (getAlgName []
      (when debug (prn 'make-identity 'getAlgName))
      (->> blob pack/decode-string first (map char) (apply str)))
    (isEncrypted []
      (when debug (prn 'make-identity 'isEncrypted))
      false)
    (setPassphrase [passphrase]
      (when debug (prn 'make-identity 'setPassphrase))
      true)))

(def identity-repository-unavailable 0)
(def identity-repository-not-running 1)
(def identity-repository-running 2)

(defn make-identity-repository []
  (proxy [IdentityRepository] []
    (getIdentities []
      (when debug (prn 'make-identity-repository 'getIdentities))
      (let [sock (open-auth-socket)
            identites (request-identities sock)]
        (when debug (prn 'make-identity-repository 'close-auth-socket))
        (close-auth-socket sock)
        (when debug (prn 'make-identity-repository 'close-auth-socket 'closed))
        (Vector.
         (mapv make-identity identites))))
    (getName []
      (when debug (prn 'make-identity-repository 'getName))
      "ssh-agent")
    (getStatus []
      (when debug (prn 'make-identity-repository 'getStatus))
      identity-repository-running)))
