(ns spire.pod.stream)

;; a partial implementation of the java stream interfaces
;; that can be used to stream data across the spire/babashka boundary.

;; these are simple implemenations of stream read and write that
;; can be called across the boundary
(defn write-byte [stream byte]
  (.write stream byte))

(defn write-bytes [stream bytes]
  (let [a (into-array byte bytes)]
    (.write stream a 0 (count a))))

(defn read-byte [stream]
  (.read stream))

(defn read-bytes [stream length]
  (prn stream length)
  (let [a (byte-array length)
        result (.read stream a 0 length)]
    [result (seq a)]))

(defn receive [stream b]
  (.receive stream b))

(defn close [stream]
  (.close stream))

(defn available [stream]
  (.available stream))

(defn connect [stream sink]
  (.connect stream sink))

;; the following is only used on the bb side, to wrap the serialisable calls
;; into a bb PipedInputStream
(defn make-piped-input-stream [stream-key]
  (proxy [java.io.PipedInputStream] []
    (available []
      (available stream-key))
    (close []
      (close stream-key))
    (read
      ([]
       (read-byte stream-key))
      ([byte-arr off len]
       (if (zero? len)
         0
         (let [[result data] (read-bytes stream-key len)]
           (loop [d (seq data)
                  o off
                  bytes result]
             (if (and d (pos? bytes))
               (do
                 (aset byte-arr o (byte (first d)))
                 (recur (next d) (inc o) (dec bytes)))
               result))))))
    (receive [b]
      (receive stream-key b))))

(defn make-piped-output-stream [stream-key]
  (proxy [java.io.PipedOutputStream] []
    (close [this]
      (close stream-key))
    (connect [this sink]
      (connect stream-key sink)
      )
    (flush [this]
      (flush stream-key)
      )
    (write
      ([this b]
       (write-byte stream-key b))
      ([this byte-arr off len]
       (write-bytes
        stream-key
        (doall (for [n (range len)] (aget byte-arr (+ off n)))))))))
