(ns spire.shell
  "A simple but flexible library for shelling out from Clojure."
  (:refer-clojure :exclude [flush read-line])
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io InputStream OutputStream]))

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

(defn make-return-data [window truncate]
  (apply
   str
   (map char
        (subvec window 0
                (- (count window) truncate)))))

(defn capture-until
  "consume bytes from out-reader until the passed in string marker is found
  then stop."
  [reader string]
  (let [search-for (map int string)
        length (count string)]
    (loop [window []]
      (let [next-char (.read reader)]
        (if (= -1 next-char)
          [:ended (make-return-data window 0)]
          (let [new-window (conj window next-char)
                compare-window (subvec new-window (max 0 (- (count new-window) length)))]
            (if (= compare-window search-for)
              [:found (make-return-data new-window length)]
              (recur new-window))))))))

(def slug-choice "0123456789abcdefghijklmnopqrstuvwxyz")

(defn make-slug []
  (->> slug-choice
       rand-nth
       (for [_ (range 8)])
       (apply str)))

(defn run-raw [{:keys [out-reader err-reader] :as proc} command]
  (let [out-start (make-slug)
        out-end (make-slug)
        err-start (make-slug)
        err-end (make-slug)]
    (feed-from-string proc (format "echo -n %s; echo -n %s 1>&2\n" out-start err-start))
    (feed-from-string proc (str command "\n"))
    (feed-from-string proc (format "echo -n %s; echo -n %s 1>&2\n" out-end err-end))
    (capture-until out-reader out-start)
    (capture-until err-reader err-start)
    (let [[_ out] (capture-until out-reader out-end)
          [_ err] (capture-until err-reader err-end)]
      {:out out
       :err err})))

(defn run [proc command]
  (let [result (run-raw proc (str command "; EXIT_CODE=$?"))
        {:keys [out]} (run-raw proc "echo $EXIT_CODE")
        exit-code (Integer/parseInt (string/trim out))]
    (assoc result :exit exit-code)))
