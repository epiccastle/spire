(ns spire.scp
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.nio :as nio]
            [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io InputStream OutputStream File FileOutputStream
            PipedInputStream PipedOutputStream StringReader]
           [com.jcraft.jsch ChannelExec]
           ))

;; https://web.archive.org/web/20170215184048/https://blogs.oracle.com/janp/entry/how_the_scp_protocol_works

(comment)
(def debug println)
(def debugf (comp println format))

(comment
  (defmacro debug [& args])
  (defmacro debugf [& args]))

(defn- scp-send-ack
  "Send acknowledgement to the specified output stream"
  [^OutputStream out]
  (.write out (byte-array [0]))
  (.flush out))

(defn- scp-receive-ack
  "Check for an acknowledgement byte from the given input stream"
  [^InputStream in]
  (let [code (.read in)]
    (when-not (zero? code)
      ;; TODO: error should be read over stderr. we have bundled
      ;; stderr on stdout. Not elegant.
      (let [msg (loop [c (.read in)
                       s ""]
                  (if (#{10 13 -1 0} c)
                    s
                    (recur (.read in) (str s (char c)))))]
        (throw (ex-info "scp error" {:code code
                                              :msg msg}))))))

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
    :or {mode 0644 buffer-size (* 256 1024) preserve false}}
   & [progress-context]]
  (debug "scp-copy-file:" progress-context)
  (when preserve
    #_(let [pfile (PATH)])
    (scp-send-command
     send recv
     (format "T%d 0 %d 0"
             (nio/last-modified-time file)
             (nio/last-access-time file))))
  (scp-send-command
   send recv
   (format "C%04o %d %s"
           (if preserve
             (nio/file-mode file)
             mode)
           (.length file) (.getName file)))
  (debugf "Sending %s" (.getAbsolutePath file))
  (let [final-progress-context
        (if progress-fn
          (let [size (.length file)
                input-stream (io/input-stream file)
                chunk-size buffer-size
                chunk (byte-array chunk-size)]
            (loop [offset 0 context progress-context]
              (debug "PC:" progress-context)
              (let [bytes-read (.read input-stream chunk)]
                (if (= -1 bytes-read)
                  ;; we will get an immediate EOF when its a zero byte file
                  (do
                    (debug "EOF")
                    (progress-fn file offset size 1.0 context))

                  ;; some btyes read
                  (let [new-offset (+ bytes-read offset)]
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
                        (progress-fn file new-offset size (float (/ new-offset size)) context))))))))
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
    :or {mode 0644 buffer-size (* 256 1024) preserve false}}]
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
  [send recv ^File dir {:keys [dir-mode skip-files preserve] :or {dir-mode 0755} :as options}
   & [progress-context]]
  (debug "scp-copy-dir progress-context:" progress-context)
  (debugf "Sending directory %s" (.getAbsolutePath dir))
  (when preserve
    #_(let [pfile (PATH)])
    (scp-send-command
     send recv
     (format "T%d 0 %d 0"
             (nio/last-modified-time dir)
             (nio/last-access-time dir))))
  (scp-send-command
   send recv
   (format "D%04o 0 %s"
           (if preserve
             (nio/file-mode dir)
             dir-mode)
           (.getName dir)))
  (let [final-progress-context
        (loop [[file & remain] (.listFiles dir)
               progress-context progress-context]
          #_ (when file
            (println "skip?" (.getPath file) "res:" (boolean (skip-files (.getPath file)))))
          (if file
            (cond
              (and (.isFile file) (not (skip-files (.getPath file))))
              (do
                (debug "copy remain:" remain)
                (recur remain
                       (update (scp-copy-file send recv file options progress-context)
                               :fileset-file-start + (.length file))))

              (.isFile file)
              (do
                (debug "skip remain:" remain)
                (recur remain progress-context))

              (.isDirectory file)
              (do
                (debug "isdir remain" remain)
                (recur remain (scp-copy-dir send recv file options progress-context)))

              :else
              (recur remain progress-context))

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

(defn scp-to
  "Copy local path(s) to remote path via scp"
  [session local-paths remote-path & {:keys [recurse shell-fn stdin-fn exec exec-fn]
                                      :as opts
                                      :or {shell-fn identity
                                           stdin-fn identity}}]
  (let [local-paths? (sequential? local-paths)
        local-paths (if local-paths? local-paths [local-paths])
        ;;files (scp-files local-paths recurse)
        ]
    (let [[^PipedInputStream in
           ^PipedOutputStream send] (ssh/streams-for-in)
          cmd (format "scp %s %s -t %s" (:remote-flags opts "") (if recurse "-r" "") remote-path)
          _ (debugf "scp-to: %s using executor %s" cmd (str exec))
          _ (prn exec-fn session shell-fn stdin-fn opts)
          _ (prn session (str "umask 0000;" (shell-fn cmd)) (stdin-fn in) :stream opts)
          {:keys [out-stream]}
          (if (= exec :local)
            (exec-fn nil
                     (shell-fn
                      (format "bash -c 'umask 0000; %s'" cmd))
                     (stdin-fn in) :stream opts)
            (do
              (prn 1)
              (exec-fn session (str "umask 0000;" (shell-fn cmd)) (stdin-fn in) :stream opts)))
          _ (prn 2)
          recv out-stream]
      (debugf "scp-to %s %s" (string/join " " local-paths) remote-path)
      (debug "Receive initial ACK")
      (scp-receive-ack recv)
      (doseq [file local-paths]
        (let [file (io/file file)]
          (if (string? file)
            (debugf "scp-to: from %d bytes" (count file))
            (debugf "scp-to: from %s name: %s"
                    (.getPath file)
                    (.getName file)))

          (cond
            (utils/content-recursive? file)
            (do
              (debugf "%s is recursive" file)
              (scp-copy-dir send recv file opts
                            {:fileset-file-start 0}))

            (utils/content-file? file)
            (do
              (debugf "%s is plain file" file)
              (scp-copy-file send recv file opts)))))
      (debug "Closing streams")
      (.close send)
      (.close recv)
      (debug "streams closed")

      ;; files copied?
      true)))


(defn scp-content-to
  "Copy local path(s) to remote path via scp"
  [session content remote-path & {:keys [recurse shell-fn stdin-fn exec exec-fn]
                                  :as opts
                                  :or {shell-fn identity
                                       stdin-fn identity}}]
  (let [[^PipedInputStream in
         ^PipedOutputStream send] (ssh/streams-for-in)
        cmd (format "scp %s %s -t %s" (:remote-flags opts "") (if recurse "-r" "") remote-path)
        _ (debugf "scp-to: %s using executor %s" cmd (str exec))
        {:keys [out-stream]}
        (if (= exec :local)
          (exec-fn nil
                   (shell-fn
                    (format "bash -c 'umask 0000; %s'" cmd))
                   (stdin-fn in) :stream opts)
          (exec-fn session (str "umask 0000;" (shell-fn cmd)) (stdin-fn in) :stream opts))
        recv out-stream]
    (debugf "scp-to %d bytes of content to %s" (utils/content-size content) remote-path)
    (debug "Receive initial ACK")
    (scp-receive-ack recv)
    (scp-copy-data send recv
                   content
                   (utils/content-size content) (.getName (io/file remote-path))
                   opts)
    (debug "Closing streams")
    (.close send)
    (.close recv)
    (debug "streams closed")

    ;; files copied?
    true))


(defn scp-parse-times
  [cmd]
  (let [s (StringReader. cmd)]
    (.skip s 1) ;; skip T
    (let [scanner (java.util.Scanner. s)
          mtime (.nextLong scanner)
          zero (.nextInt scanner)
          atime (.nextLong scanner)]
      [mtime atime])))

(defn scp-parse-copy
  [cmd]
  (let [s (StringReader. cmd)]
    (.skip s 1) ;; skip C or D
    (let [scanner (java.util.Scanner. s)
          mode (.nextInt scanner 8)
          length (.nextLong scanner)
          filename (.next scanner)]
      (debug "returning scp-parse-copy:" mode length filename)
      [mode length filename])))

(defn- scp-receive-command
  "Receive command on the specified input stream"
  [^OutputStream out ^InputStream in]
  (let [buffer-size 1024
        buffer (byte-array buffer-size)]
    (let [cmd (loop [offset 0]
                (let [n (.read in buffer offset (- buffer-size offset))]
                  (debugf
                   "scp-receive-command: %d %s"
                   (first buffer)
                   (String. buffer (int 0) (int (+ offset n))))
                  (if (= \newline (char (aget buffer (+ offset n -1))))
                    (String. buffer (int 0) (int (+ offset n)))
                    (recur (+ offset n)))))]
      (debugf "Received command %s" cmd)
      (scp-send-ack out)
      (debug "Sent ACK")
      cmd)))

(defn scp-sink-file
  "Sink a file"
  [^OutputStream send ^InputStream recv
   ^File file mode length {:keys [buffer-size progress-fn] :or {buffer-size (* 256 1024)}} & [progress-context]]
  (debugf "Sinking %d bytes to file %s" length (.getPath file))
  (let [buffer (byte-array buffer-size)
        final-progress-context
        (with-open [file-stream (FileOutputStream. file)]
          (loop [offset 0
                 context progress-context]
            (let [size (.read recv buffer 0 (min (- length offset) buffer-size))
                  new-offset (if (pos? size) (+ offset size) offset)]
              (debug 1)
              (when (pos? size)
                (.write file-stream buffer 0 size))
              (debug 2 size new-offset length)
              (if (and (pos? size) (< new-offset length))
                (do
                  (debug 3)
                  (recur
                   new-offset
                   (progress-fn file new-offset length (float (/ new-offset length)) context)))

                ;; last chunk written
                (do
                  (debug 4)
                  (let [res (progress-fn file new-offset length
                                         (if (pos? length)
                                           (float (/ new-offset length))
                                           1.0)
                                         context)]
                    (debug 5)
                    res))))))]
    (debug 6)
    (scp-receive-ack recv)
    (debug "Received ACK after sink of file")
    (scp-send-ack send)
    (debug "Sent ACK after sink of file")
    (debug "final:" final-progress-context)
    final-progress-context
    ))

(defn scp-sink
  "Sink scp commands to file"
  [^OutputStream send ^InputStream recv ^File file times {:keys [progress-fn] :as options}
   & [progress-context]]
  (loop [cmd (scp-receive-command send recv)
         file file
         times times
         depth 0
         context progress-context]
    (debug "...." file ">" depth "[" times "]")
    ;;(Thread/sleep 1000)
    (case (first cmd)
      \C (do
           (debug "\\C")
           (let [[mode length ^String filename] (scp-parse-copy cmd)
                 nfile (if (and (.exists file) (.isDirectory file))
                         (File. file filename)
                         file)]
             (when (.exists nfile)
               (.delete nfile))
             (nio/create-file nfile mode)
             (let [new-context
                   (update (scp-sink-file send recv nfile mode length options context)
                           :fileset-file-start + length)]
               (when times
                 (nio/set-last-modified-and-access-time nfile (first times) (second times)))
               (if (pos? depth)
                 (recur (scp-receive-command send recv) file nil depth new-context)

                 ;; no more files. return
                 new-context
                 ))))
      \T (do
           (debug "\\T")
           (recur (scp-receive-command send recv) file (scp-parse-times cmd) depth context))
      \D (do
           (debug "\\D")
           (let [[mode _ ^String filename] (scp-parse-copy cmd)
                 dir (File. file filename)]
             (when (and (.exists dir) (not (.isDirectory dir)))
               (.delete dir))
             (when (not (.exists dir))
               (.mkdir dir))
             (recur (scp-receive-command send recv) dir nil (inc depth) context)))
      \E (do
           (debug "\\E")
           (let [new-depth (dec depth)]
             (when (pos? new-depth)
               (recur (scp-receive-command send recv) (io/file (.getParent file)) nil new-depth context))))

      (when cmd
        (when (= 1 (int (first cmd)))
          ;; TODO: what to do with the error message?
          (let [[error next-cmd] (string/split (subs cmd 1) #"\n")]
            (println "WARNING:" error)
            (recur next-cmd file nil depth context)))))))


(defn scp-from
  "Copy remote path(s) to local path via scp."
  [session remote-paths ^String local-path
   & {:keys [username password port mode dir-mode recurse preserve shell-fn stdin-fn exec-fn]
      :as opts
      :or {shell-fn identity
           stdin-fn identity}}]
  (let [remote-paths? (sequential? remote-paths)
        remote-paths (if remote-paths? remote-paths [remote-paths])
        ;;files (scp-files local-paths recurse)
        results
        (for [remote-path remote-paths]
          (let [file (File. local-path)
                [^PipedInputStream in
                 ^PipedOutputStream send] (ssh/streams-for-in)
                flags {:recurse "-r" :preserve "-p"}
                cmd (format
                     "scp %s -f %s"
                     (:remote-flags
                      opts
                      (string/join
                       " "
                       (->>
                        (select-keys opts [:recurse :preserve])
                        (filter val)
                        (map (comp flags key)))))
                     remote-path
                     #_(string/join " " remote-paths))
                _ (debugf "scp-from: %s" cmd)
                {:keys [^ChannelExec channel
                        out-stream]}
                (exec-fn session (shell-fn cmd) (stdin-fn in) :stream opts)
                exec channel
                recv out-stream]
            (debugf
             "scp-from %s %s" remote-path local-path)
            (scp-send-ack send)
            (debug "Sent initial ACK")
            (scp-sink send recv file nil opts {:fileset-file-start 0})
            (debug "Closing streams")
            (.close send)
            (.close recv)
            (debug "closed")
            true))]
    (some identity results)
    ))
