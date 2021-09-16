(ns spire.pod.stream)

;; a partial implementation of the java stream interfaces
;; that can be used to stream data across the spire/babashka boundary.

(defn encode [to-encode]
  (.encodeToString (java.util.Base64/getEncoder) to-encode))

(defn decode [to-decode]
  (.decode (java.util.Base64/getDecoder) to-decode))

;; these are simple implemenations of stream read and write that
;; can be called across the boundary

;;
;; PipedInputStream
;;
(defn available [^java.io.PipedInputStream stream]
  (.available stream))

(defn close-input-stream [^java.io.PipedInputStream stream]
  (.close stream))

(defn read-byte [^java.io.PipedInputStream stream]
  (.read stream))

(defn read-bytes [^java.io.PipedInputStream stream length]
  (let [a (byte-array length)
        result (.read stream a 0 length)]
    [result (encode a)]))

(defn receive [^java.io.PipedInputStream stream b]
  (.receive stream b))

;; the following is only used on the bb side, to wrap the serialisable calls
;; into a bb PipedInputStream
(defn make-piped-input-stream [stream-key]
  (proxy [java.io.PipedInputStream] []
    (available []
      (available stream-key))
    (close []
      (close-input-stream stream-key))
    (read
      ([]
       (read-byte stream-key))
      ([byte-arr off len]
       (if (zero? len)
         0
         (let [[result data] (read-bytes stream-key len)]
           (when (pos? result)
             (System/arraycopy
              (decode data) 0
              byte-arr off result))
           result))))
    (receive [b]
      (receive stream-key b))))


;;
;; PipedOutputStream
;;
(defn close-output-stream [stream]
  (.close stream))

(defn connect [stream ^java.io.PipedInputStream sink]
  (.connect stream sink))

(defn flush-output-stream [stream]
  (.flush stream))

(defn write-byte [stream byte]
  (.write stream byte))

(defn write-bytes [stream bytes]
  (let [a (decode bytes)]
    (.write stream a 0 (count a))))


;; the following is only used on the bb side, to wrap the serialisable calls
;; into a bb PipedOutputStream
(defn make-piped-output-stream [stream-key]
  (proxy [java.io.PipedOutputStream] []
    (close []
      (close-output-stream stream-key))
    (connect [sink]
      (connect stream-key sink)
      )
    (flush []
      (flush-output-stream stream-key)
      )
    (write
      ([b]
       (write-byte stream-key b))
      ([byte-arr off len]
       (write-bytes
        stream-key
        (encode
         (java.util.Arrays/copyOfRange byte-arr off (+ off len))))))))
