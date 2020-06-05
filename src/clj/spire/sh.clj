(ns spire.sh
  (:require [spire.shlex :as shlex]
            [clojure.java.io :as io])
  (:import [java.io PipedInputStream PipedOutputStream ByteArrayOutputStream]))

"A clojure.java.sh replacement that support streaming"

(set! *warn-on-reflection* true)

(defn proc
  "Spin off another process.
  Returns a hash map with `:in-stream`, `:in-writer`, `:out-stream`,
  `:out-reader`, `:err-stream`, `:err-reader` and `:process`"
  [cmd]
  (let [builder (ProcessBuilder. ^"[Ljava.lang.String;" (into-array String cmd))
        process (.start builder)
        out-stream (.getInputStream process)
        in-stream (.getOutputStream process)
        err-stream (.getErrorStream process)]
    {:out-stream out-stream
     :out-reader (io/reader out-stream)
     :in-stream in-stream
     :in-writer (io/writer in-stream)
     :err-stream err-stream
     :err-reader (io/reader err-stream)
     :process process}))

(defn feed-from
  "Feed to a process's input stream with optional. Options passed are
  fed to clojure.java.io/copy. They are :encoding to set the encoding
  and :buffer-size to set the size of the buffer. :encoding defaults to
  UTF-8 and :buffer-size to 1024."
  [{:keys [in-stream]} from & [opts]]
  (apply io/copy from in-stream opts))

(defn feed-from-string
  "Feed the process some data from a string."
  [process s & args]
  (apply feed-from process (java.io.StringReader. s) args))

(def
  ^{:dynamic true
    :doc "The buffer size (in bytes) for the piped stream used to implement
    the :stream option for :out. If your ssh commands generate a high volume of
    output, then this buffer size can become a bottleneck. You might also
    increase the frequency with which you read the output stream if this is an
    issue."}
  *piped-stream-buffer-size* (* 1024 10))

(defn- streams-for-out
  [out]
  (if (= :stream out)
    (let [os (PipedOutputStream.)]
      [os (PipedInputStream. os (int *piped-stream-buffer-size*))])
    [(ByteArrayOutputStream.) nil]))

(defn streams-for-in
  []
  (let [os (PipedInputStream. (int *piped-stream-buffer-size*))]
    [os (PipedOutputStream. os)]))

(defn read-all-bytes [input-stream]
  (->>
   (loop [out []]
     (let [avail (.available ^java.io.InputStream input-stream)]
       (if (pos? avail)
         (let [out-array (byte-array avail)]
           (.read ^java.io.InputStream input-stream out-array 0 avail)
           (recur (conj out out-array)))
         ;; might be at end of stream. try and read.
         (let [res (.read ^java.io.InputStream input-stream)]
           (if-not (= -1 res)
             (recur (conj out (byte-array [res])))
             ;;end of stream
             out)))))
   (apply concat)))

(defn exec [cmd in out opts]
  (let [{:keys [out-reader
                out-stream
                in-writer
                in-stream
                err-reader
                err-stream
                process] :as result} (proc (shlex/parse cmd))]
    (if (string? in)
      (do
        (feed-from-string result in)
        (.close ^java.io.OutputStream in-stream))
      ;; java.io.PipedInputStream
      (future
        (loop []
          (let [avail (.available ^java.io.InputStream in)]
            (if (pos? avail)
              (let [out-array (byte-array avail)
                    res (.read ^java.io.InputStream in out-array 0 avail)]
                (when-not (= -1 res)
                  (.write ^java.io.OutputStream in-stream out-array 0 avail)
                  (.flush ^java.io.OutputStream in-stream) ;; have to force it to be unbuffered for chatty protocols like scp
                  (recur)))
              ;; maybe we are at end of stream. try and read
              (let [res (.read ^java.io.InputStream in)]
                (when-not (= -1 res)
                  (.write ^java.io.OutputStream in-stream res)
                  (.flush ^java.io.OutputStream in-stream) ;; have to force it to be unbuffered for chatty protocols like scp
                  (recur))))))
        (.close ^java.io.OutputStream in-stream)))
    (let [output
          (cond
            (= :stream out) out-stream
            (= :bytes out) (read-all-bytes out-stream)
            (string? out) (slurp out-reader))

          error
          (cond
            (= :stream out) err-stream
            (= :bytes out) (read-all-bytes err-stream)
            (string? out) (slurp err-reader))
          ]
      (if (= :stream out)
        {:channel process
         :out-stream output
         :err-stream error}
        {:exit (.waitFor ^java.lang.Process process)
         :out output
         :err error}))))

#_ (exec "ls -alF" "" "UTF-8" {})
#_ (exec "ls -alF" "" :stream {})
#_ (exec "ls -alF" "" :bytes {})
#_ (exec "bash" "hostname" "UTF-8" {})
#_ (exec "bash -c 'umask 0000'" "" "UTF-8" {})

#_ (shlex/parse "ls -alF")
