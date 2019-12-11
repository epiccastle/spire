(ns spire.scp
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io InputStream OutputStream File
            PipedInputStream PipedOutputStream]
           [com.jcraft.jsch ChannelExec]))

;; https://web.archive.org/web/20170215184048/https://blogs.oracle.com/janp/entry/how_the_scp_protocol_works

(comment
  (def debug println)
  (def debugf (comp println format)))

(comment)
(defmacro debug [& args])
(defmacro debugf [& args])

(defn- scp-send-ack
  "Send acknowledgement to the specified output stream"
  [^OutputStream out]
  (.write out (byte-array [0]))
  (.flush out))

(defn- scp-receive-ack
  "Check for an acknowledgement byte from the given input stream"
  [^InputStream in]
  (let [code (.read in)]
    (assert (zero? code) "scp protocol error")))

(defn- scp-send-command
  "Send command to the specified output stream"
  [^OutputStream out ^InputStream in ^String cmd-string]
  (.write out (.getBytes (str cmd-string "\n")))
  (.flush out)
  (debugf "Sent command %s" cmd-string)
  (scp-receive-ack in)
  (debug "Received ACK"))

(defn- scp-copy-file
  "Send acknowledgement to the specified output stream"
  [^OutputStream send ^InputStream recv ^File file
   {:keys [mode buffer-size preserve progress-fn]
    :or {mode 0644 buffer-size (* 32 1024) preserve false}}
   & [progress-context]]
  (debug "scp-copy-file:" progress-context)
  (when preserve
    (scp-send-command
     send recv
     (format "P%d 0 %d 0" (.lastModified file) (.lastModified file))))
  (scp-send-command
   send recv
   (format "C%04o %d %s" mode (.length file) (.getName file)))
  (debugf "Sending %s" (.getAbsolutePath file))
  (let [final-progress-context
        (if progress-fn
          (let [size (.length file)
                input-stream (io/input-stream file)
                chunk-size buffer-size
                chunk (byte-array chunk-size)]
            (loop [offset 0 context progress-context]
              (debug "PC:" progress-context)
              (let [bytes-read (.read input-stream chunk)
                    new-offset (+ bytes-read offset)]
                (debugf "bytes read: %d" bytes-read)
                (if (= bytes-read chunk-size)
                  ;; full chunk
                  (do
                    (debug "full")
                    (io/copy chunk send)
                    (.flush send)
                    (recur new-offset (progress-fn file new-offset size (float (/ new-offset size)) context)))

                  ;; last partial chunk
                  (do
                    (debug "partial")
                    (io/copy (byte-array (take bytes-read chunk)) send)
                    (.flush send)
                    (progress-fn file new-offset size (float (/ new-offset size)) context))))))
          ;; no progress callback
          (io/copy file send :buffer-size buffer-size))]
    (scp-send-ack send)
    (debug "Receiving ACK after send")
    (scp-receive-ack recv)
    (debug "final:" final-progress-context)
    final-progress-context
    ))

(defn- scp-copy-data
  "Send acknowledgement to the specified output stream"
  [^OutputStream send ^InputStream recv data size dest-name
   {:keys [mode buffer-size progress-fn]
    :or {mode 0644 buffer-size (* 32 1024) preserve false}}]
  (let [size size #_(count data)]
    (scp-send-command
     send recv
     (format "C%04o %d %s" mode size dest-name))
    (debugf "Sending %d bytes. data: %s" size data)
    (if progress-fn
      (let [input-stream (utils/content-stream data)
            chunk-size buffer-size
            chunk (byte-array chunk-size)]
        (loop [offset 0 context nil]
          (let [bytes-read (.read input-stream chunk)
                new-offset (+ bytes-read offset)]
            (debugf "bytes read: %d" bytes-read)
            (if (= bytes-read chunk-size)
              ;; full chunk
              (do
                (debug "full")
                (io/copy chunk send)
                (.flush send)
                (recur new-offset (progress-fn data new-offset size (float (/ new-offset size)) context)))

              ;; last partial chunk
              (do
                (debug "partial")
                (io/copy (byte-array (take bytes-read chunk)) send)
                (.flush send)
                (progress-fn data new-offset size (float (/ new-offset size)) context))))))
      ;; no progress callback
      (io/copy (io/input-stream data) send :buffer-size buffer-size))
    (scp-send-ack send)
    (debug "Receiving ACK after send")
    (scp-receive-ack recv)))

(defn- scp-copy-dir
  "Send acknowledgement to the specified output stream"
  [send recv ^File dir {:keys [dir-mode skip-files] :or {dir-mode 0755} :as options} & [progress-context]]
  (debug "scp-copy-dir progress-context:" progress-context)
  (debugf "Sending directory %s" (.getAbsolutePath dir))
  (scp-send-command
   send recv
   (format "D%04o 0 %s" dir-mode (.getName dir)))
  (let [final-progress-context
        (loop [[file & remain] (.listFiles dir)
               progress-context progress-context]
          (if file
            #_ (println "file:" file "skip?" (skip-files (.getPath file)))
            (cond
              (and (.isFile file) (not (skip-files (.getPath file))))
              (recur remain
                     (update (scp-copy-file send recv file options progress-context)
                             :fileset-file-start + (.length file)))

              (.isDirectory file)
              (recur remain (scp-copy-dir send recv file options progress-context)))

            progress-context))]
    (scp-send-command send recv "E")
    final-progress-context))

(defn- scp-files
  [paths recurse]
  (let [f (if recurse
            #(File. ^String %)
            (fn [^String path]
              (let [file (File. path)]
                (when (.isDirectory file)
                  (throw
                   (ex-info
                    (format
                     "Copy of dir %s requested without recurse flag" path)
                    {:type :clj-ssh/scp-directory-copy-requested})))
                file)))]
    (map f paths)))

#_(defn scp-to
  "Copy local path(s) to remote path via scp.
   Options are:
   :username   username to use for authentication
   :password   password to use for authentication
   :port       port to use if no session specified
   :mode       mode, as a 4 digit octal number (default 0644)
   :dir-mode   directory mode, as a 4 digit octal number (default 0755)
   :recurse  flag for recurse operation
   :preserve   flag for preserving mode, mtime and atime. atime is not available
               in java, so is set to mtime. mode is not readable in java."
  [session local-paths remote-path
   & {:keys [username password port mode dir-mode recurse preserve progress-fn] :as opts}]
  (let [local-paths (if (sequential? local-paths) local-paths [local-paths])
        files (scp-files local-paths recurse)]
    (let [[^PipedInputStream in
           ^PipedOutputStream send] (ssh/streams-for-in)
          cmd (format "scp %s %s -t %s" (:remote-flags opts "") (if recurse "-r" "") remote-path)
          _ (debugf "scp-to: %s" cmd)
          {:keys [^PipedInputStream out-stream]}
          (ssh/ssh-exec session cmd in :stream opts)
          recv out-stream]
      (debugf "scp-to %s %s" (string/join " " local-paths) remote-path)
      (debug "Receive initial ACK")
      (scp-receive-ack recv)
      (doseq [^File file files]
        (debugf "scp-to: from %s" (.getPath file))
        (if (.isDirectory file)
          (scp-copy-dir send recv file opts)
          (scp-copy-file send recv file opts)))
      (debug "Closing streams")
      (.close send)
      (.close recv)
      nil)))

#_(defn scp-data-to
  "Copy local path(s) to remote path via scp.
   Options are:
   :username   username to use for authentication
   :password   password to use for authentication
   :port       port to use if no session specified
   :mode       mode, as a 4 digit octal number (default 0644)
   :dir-mode   directory mode, as a 4 digit octal number (default 0755)
   :recurse  flag for recurse operation
   :preserve   flag for preserving mode, mtime and atime. atime is not available
               in java, so is set to mtime. mode is not readable in java."
  [session data remote-path & {:as opts}]
  (let [[^PipedInputStream in
         ^PipedOutputStream send] (ssh/streams-for-in)
        cmd (format "scp %s -t %s" (:remote-flags opts "") remote-path)
        _ (debugf "scp-data-to: %s" cmd)
        {:keys [^PipedInputStream out-stream]}
        (ssh/ssh-exec session cmd in :stream opts)
        recv out-stream]
    #_ (debugf "scp-data-to %s %s" (string/join " " local-paths) remote-path)
    (debug "Receive initial ACK")
    (scp-receive-ack recv)
    (debugf "scp-data-to: from %d" (count data))
    (scp-copy-data
     send recv
     (if (string? data)
       (.getBytes data)
       data)
     (if (string? data)
       (count data)
       (.length data))
     (.getName (io/file remote-path))
     opts)
    (debug "Closing streams")
    (.close send)
    (.close recv)
    nil))

;; (.length (io/file "spire"))

;; (.length (.getBytes "spire"))

(defn scp-to
  "Copy local path(s) to remote path via scp"
  [session local-paths remote-path & {:keys [recurse fileset-total max-filename-length] :as opts}]
  (let [local-paths (if (sequential? local-paths) local-paths [local-paths])
        ;;files (scp-files local-paths recurse)
        ]
    (let [[^PipedInputStream in
           ^PipedOutputStream send] (ssh/streams-for-in)
          cmd (format "scp %s %s -t %s" (:remote-flags opts "") (if recurse "-r" "") remote-path)
          _ (debugf "scp-to: %s" cmd)
          {:keys [^PipedInputStream out-stream]}
          (ssh/ssh-exec session cmd in :stream opts)
          recv out-stream]
      (debugf "scp-to %s %s" (string/join " " local-paths) remote-path)
      (debug "Receive initial ACK")
      (scp-receive-ack recv)
      (doseq [file-or-data local-paths]
        (debugf "scp-to: from %s name: %s" (.getPath file-or-data) (.getName file-or-data))
        (cond
          (utils/content-recursive? file-or-data)
          (scp-copy-dir send recv file-or-data opts
                        {:fileset-total fileset-total
                         :fileset-file-start 0
                         :max-filename-length max-filename-length})

          (utils/content-file? file-or-data)
          (scp-copy-file send recv file-or-data opts)

          :else
          (scp-copy-data send recv
                         file-or-data (utils/content-size file-or-data) (.getName (io/file remote-path))
                         opts
                         )
          ))
      (debug "Closing streams")
      (.close send)
      (.close recv)

      ;; files copied?
      true)))
