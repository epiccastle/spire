(ns spire.known-hosts
  (:require [spire.pack :as pack]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [edamame.core :as edamame])
  (:import [org.apache.commons.codec.binary Base64]
           [org.apache.commons.codec.digest HmacUtils HmacAlgorithms]
           [com.jcraft.jsch HostKey HostKeyRepository]
           ))

(defn process-hosts [hosts]
  (->> (string/split hosts #",")
       (into #{})))

(defn parse-line [filename line-num line]
  (when-not (or (string/starts-with? (string/trim line) "#")
                (zero? (count (string/trim line))))
    (let [parts (string/split line #"\s+" -1)
          revoked? (= "@revoked" (first parts))
          cert-authority? (= "@cert-authority" (first parts))
          non-marked-parts (if (or revoked? cert-authority?)
                             (rest parts)
                             parts)
          zos-key-ring-label? (.contains ^String line "zos-key-ring-label")
          non-marked-parts (if zos-key-ring-label?
                             [(first non-marked-parts) (string/join " " (rest non-marked-parts))]
                             non-marked-parts)
          num (count non-marked-parts)
          host (case num
                 5 (let [[hosts bits exponent modulus comment] non-marked-parts]
                     {:hosts hosts
                      :bits (Integer/parseInt bits)
                      :exponent (edamame/parse-string exponent)
                      :modulus (edamame/parse-string modulus)
                      :comment comment})
                 4 (let [[hosts type key comment] non-marked-parts]
                     {:hosts hosts
                      :type (keyword type)
                      :key key
                      :comment comment})
                 3 (let [[hosts type key] non-marked-parts]
                     {:hosts hosts
                      :type (keyword type)
                      :key key})
                 2 (let [[hosts key-ring] non-marked-parts
                         [label value] (string/split key-ring #"=")]
                     {:hosts hosts
                      (keyword label) (edamame/parse-string value)})
                 (throw (ex-info "malformed hosts line" {:filename filename
                                                         :line-num line-num
                                                         :line line})))
          annotated (if (or revoked? cert-authority?)
                      (assoc host
                             :marker (cond revoked? :revoked cert-authority? :cert-authority)
                             :revoked? revoked?
                             :cert-authority? cert-authority?)
                      host)]
      (-> annotated
          (update :hosts process-hosts)
          (assoc :filename filename
                 :line-num line-num)))))

(comment
  (parse-line "filename" 10 "# this is a comment")
  (parse-line "filename" 10 "closenet,...,192.0.2.53 1024 37 1593433453453453454593 closenet.example.net")
  (parse-line "filename" 10 "cvs.example.net,192.0.2.10 ssh-rsa AAAA1234.....=")
  (parse-line "filename" 10 "|1|JfKTdBh7.....= ssh-rsa AAAA1234.....=")
  (parse-line "filename" 10 "@revoked cvs.example.net,192.0.2.10 ssh-rsa AAAA1234.....=")
  (parse-line "filename" 10 "@cert-authority |1|JfKTdBh7.....= ssh-rsa AAAA1234.....=")
  (parse-line "filename" 10 "mvs* zos-key-ring-label=\"KeyRingOwner/SSHKnownHostsRing mvs1-ssh-rsa\"")
  (parse-line "filename" 10 "[anga.funkfeuer.at]:2022,[78.41.115.130]:2022 ssh-rsa AAAAB...fgTHaojQ==")
  (parse-line "filename" 10 "sdsdfsdf"))

(defn read-known-hosts-file [filename]
  (with-open [reader (clojure.java.io/reader filename)]
    (->>
     (for [[line-num line] (map-indexed vector (line-seq reader))]
       (parse-line filename (inc line-num) line))
     (filter identity)
     (into []))))

#_ (read-known-hosts-file "/home/crispin/.ssh/known_hosts")

(defn hash-hostname-raw [salt hostname]
  (let [digest (HmacUtils/getInitializedMac
                HmacAlgorithms/HMAC_SHA_1
                salt)]
    (->> hostname
         (map int)
         byte-array
         (.update digest))
    (-> digest
        .doFinal
        Base64/encodeBase64
        String.)))

(defn hash-hostname [salt-base64 hostname]
  (hash-hostname-raw (Base64/decodeBase64 salt-base64) hostname))

#_ (= (hash-hostname "HgJpEdpLnX36OnIZukqcj1dIFHk=" "epiccastle.io")
      "fbmG7ZxLUNDo5MDO9BSfGWITzF8=")

(defn host-matches? [host-def hostname]
  (if (string/starts-with? host-def "|1|")
    (let [[_ _ salt hash] (string/split host-def #"\|")]
      (= hash (hash-hostname salt hostname)))
    (= host-def hostname))
  )

#_
(host-matches? "|1|1XO28wawVjKgS+mE9LxecgJwwKE=|TGM+ZJvW4Kp7LWU3t/+nCyOaw54=" "epiccastle.io")
#_
(host-matches? "|1|HgJpEdpLnX36OnIZukqcj1dIFHk=|fbmG7ZxLUNDo5MDO9BSfGWITzF8=" "epiccastle.io")

(defn any-host-matches? [host-set hostname]
  (some #(host-matches? % hostname) host-set))

(defn find-matching-host-entries [hosts-data hostname]
  (filter (fn [{:keys [hosts]}] (any-host-matches? hosts hostname)) hosts-data))

(defn users-known-hosts-filename []
  (io/file (System/getProperty "user.home") ".ssh/known_hosts"))

#_ (-> (users-known-hosts-filename)
       read-known-hosts-file
       (find-matching-host-entries "epiccastle.io"))

(defn decode-key [data]
  (let [[type data1] (pack/decode-string data)
        [unknown data2] (pack/decode-string data1)
        [key data3] (pack/decode-string data2)]
    (assert (empty? data3) "unexpected trailing data")
    {:type (keyword (apply str (map char type)))
     :unknown unknown
     :key key}))

(defn decode-base64-key [base64-key]
  (decode-key (map int (Base64/decodeBase64 base64-key))))

(defn make-salt []
  (byte-array (map (fn [_] (rand-int 256)) (range 20))))

(defn make-hostname-hash-string [hostname]
  (let [salt (make-salt)
        hashed (hash-hostname-raw salt hostname)
        hostname-hash-string (str "|1|" (String. (Base64/encodeBase64 salt)) "|" hashed)
        ]
    hostname-hash-string))

#_(make-hostname-hash-string "epiccastle.io")

(defn make-host-key-line [hostname type key & [comment]]
  (str
   (make-hostname-hash-string hostname)
   " "
   type
   " "
   key
   " "
   comment))

#_ (make-host-key-line "epiccastle.io" "ssh-rsa" "mykey")

(defn append-host-key-to-file [filename hostname type key & [comment]]
  (spit
   filename
   (str (make-host-key-line hostname type key comment) "\n")
   :append true))

(def host-key-repository-ok 0)
(def host-key-repository-not-included 1)
(def host-key-repository-changed 2)

(defn make-host-key-repository []
  (let [known-hosts-keys (-> (users-known-hosts-filename)
                             read-known-hosts-file)]
    (proxy [HostKeyRepository] []
      (check [hostname network-key-data]
        ;; find key
        (let [{:keys [type]} (decode-key network-key-data)
              found-key (some->> hostname
                                 (find-matching-host-entries known-hosts-keys)
                                 (filter #(= type (:type %)))
                                 first)]
          (if (:key found-key)
            ;; hostname matches. compare key
            (if (= (:key found-key) (String. (Base64/encodeBase64 network-key-data)))
              host-key-repository-ok
              host-key-repository-changed)
            host-key-repository-not-included)))
      (getHostKey
        ([])
        ([hostname type]
         (let [found-key (some->> hostname
                                  (find-matching-host-entries known-hosts-keys)
                                  (filter #(= (keyword type) (:type %)))
                                  first)]
           (when (:key found-key)
             (into-array HostKey [(HostKey. hostname (Base64/decodeBase64 (:key found-key)))])))))
      (add [hostkey userinfo]
        (append-host-key-to-file
         (users-known-hosts-filename)
         (.getHost hostkey)
         (.getType hostkey)
         (.getKey hostkey)
         (.getComment hostkey))
        (comment
          (println "add" hostkey userinfo)
          (println "host" (.getHost hostkey))
          (println "type" (.getType hostkey))
          (println "key" (.getKey hostkey))
          (println "fingerprint" (.getFingerPrint hostkey (JSch.)))
          (println "comment" (.getComment hostkey))
          (println "marker" (.getMarker hostkey)))
        ))))
