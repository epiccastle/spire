(ns spire.sh
  "A clojure.java.sh replacement that support streaming"
  (:require [spire.shlex :as shlex]
            [clojure.java.io :as io])
  (:import [java.util.concurrent]))

(set! *warn-on-reflection* false)

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
