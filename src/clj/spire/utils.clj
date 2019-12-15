(ns spire.utils
  (:require ;;[spire.scp :as scp]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [digest :as digest]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell])
  (:import [java.nio.file Paths Files LinkOption Path]
           [java.nio.file.attribute FileAttribute BasicFileAttributes BasicFileAttributeView
            PosixFilePermission PosixFilePermissions FileTime]))

(defn to-camelcase [s]
  (-> s
      (string/split #"\s+")
      (->> (map string/lower-case)
           (string/join "-"))))

(defn lsb-process [out]
  (-> out
      (string/split #"\n")
      (->> (map #(string/split % #":\s+"))
           (map (fn [[k v]] [(-> k to-camelcase keyword) v]))
           (into {}))))

(defn- escape-code [n]
  (str "\033[" (or n 0) "m"))

(def colour-map
  {:red 31
   :green 32
   :yellow 33
   :blue 34})

(defn colour [& [colour-name]]
  (escape-code (colour-map colour-name)))

(defn reverse-text [& [state]]
  (escape-code (when state 7)))

(def kilobyte 1024)
(def megabyte (* 1024 kilobyte))
(def gigabyte (* 1024 megabyte))

(defn speed-string [bps]
  (cond
    (< gigabyte bps)
    (format "%.2f GB/s" (float (/ bps gigabyte)))

    (< megabyte bps)
    (format "%.2f MB/s" (float (/ bps megabyte)))

    (< kilobyte bps)
    (format "%.2f kB/s" (float (/ bps kilobyte)))

    :else
    (format "%d B/s" (int bps))))

(defn eta-string [sec]
  (let [s (-> sec (rem 60))
        m (-> sec (quot 60) (rem 60))
        h (-> sec (quot 60) (quot 60))]
    (cond
      (and (zero? m) (zero? h))
      (format "%ds" s)

      (zero? h)
      (format "%dm%02ds" m s)

      :else
      (format "%dh%02dm%02ds" h m s))))

(defn executing-bin-path []
  (.getCanonicalPath (io/as-file "/proc/self/exe")))

(defmulti content-size type)
(defmethod content-size java.io.File [f] (.length f))
(defmethod content-size java.lang.String [f] (count f))
(defmethod content-size (Class/forName "[B") [f] (count f))

(defmulti content-display-name type)
(defmethod content-display-name java.io.File [f] (.getName f))
(defmethod content-display-name java.lang.String [f] "[String Data]")
(defmethod content-display-name (Class/forName "[B") [f] "[Byte Array]")

(defmulti content-recursive? type)
(defmethod content-recursive? java.io.File [f] (.isDirectory f))
(defmethod content-recursive? java.lang.String [f] false)
(defmethod content-recursive? (Class/forName "[B") [f] false)

(defmulti content-file? type)
(defmethod content-file? java.io.File [f] (.isFile f))
(defmethod content-file? java.lang.String [f] false)
(defmethod content-file? (Class/forName "[B") [f] false)

(defmulti content-stream type)
(defmethod content-stream java.io.File [f] (io/input-stream f))
(defmethod content-stream java.lang.String [f] (io/input-stream (.getBytes f)))
(defmethod content-stream (Class/forName "[B") [f] (io/input-stream f))

(defn progress-bar [bytes total frac {:keys [start-time start-bytes]}]
  (let [
        columns (SpireUtils/get_terminal_width)
        now (time/now)
        first? (not start-time)

        duration (when-not first? (/ (float (time/in-millis (time/interval start-time now))) 1000))
        bytes-since-start (when-not first? (- bytes start-bytes))
        bytes-per-second (when (some-> duration pos?) (int (/ bytes-since-start duration)))
        bytes-remaining (- total bytes)
        eta (when (and bytes-remaining bytes-per-second)
              (int (/ bytes-remaining bytes-per-second)))

        right-side-buffer 32
        width (- columns right-side-buffer)
        percent (int (* 100 frac))
        num-chars (int (* width frac))
        num-spaces (- width num-chars)
        bar (str (reverse-text true) (apply str (take num-chars (repeat " "))) (reverse-text false))
        spaces (apply str (take num-spaces (repeat " ")))

        line-str (str "\r|" bar spaces "| " percent "% "
                       (when bytes-per-second
                         (speed-string bytes-per-second))
                       (when eta
                         (str " eta:" (eta-string eta))))
        line-len (count line-str)
        eraser (apply str (take (- columns line-len 1) (repeat " ")))

        ]
    (.write *out* (str line-str eraser))
    (.flush *out*)
    {:start-time (or start-time now)
     :start-bytes (or start-bytes bytes)}))

(defn progress-stats [file bytes total frac
                      fileset-total max-filename-length
                      {:keys [start-time start-bytes fileset-file-start]}]
  (let [
        columns (SpireUtils/get_terminal_width)
        now (time/now)
        first? (not start-time)
        fileset-file-start (or fileset-file-start 0)
        fileset-total (or fileset-total total)
        fileset-copied-so-far (+ fileset-file-start bytes)

        duration (when-not first? (/ (float (time/in-millis (time/interval start-time now))) 1000))
        bytes-since-start (when-not first? (- (+ bytes fileset-file-start) start-bytes))
        bytes-per-second (when (some-> duration pos?) (int (/ bytes-since-start duration)))
        bytes-remaining (- fileset-total fileset-copied-so-far)
        eta (when (and bytes-remaining bytes-per-second)
              (int (/ bytes-remaining bytes-per-second)))

        right-side-buffer 32
        width (- columns right-side-buffer)
        percent (int (* 100 frac))
        num-chars (int (* width frac))
        num-spaces (- width num-chars)
        bar (apply str (take num-chars (repeat "=")))
        spaces (apply str (take num-spaces (repeat " ")))

        line-str (str "\r|" bar spaces "| " percent "% "
                      (when bytes-per-second
                        (speed-string bytes-per-second))
                      (when eta
                        (str " eta:" (eta-string eta))))
        line-len (count line-str)
        eraser (apply str (take (- columns line-len 1) (repeat " ")))

        ]
    {
     ;; what gets passed to the progress bar output renderer
     :progress {:file file
                :max-filename-length max-filename-length
                :bytes fileset-copied-so-far ;;bytes
                :total fileset-total ;;total
                :frac (/ fileset-copied-so-far fileset-total) ;; frac
                :bytes-per-second bytes-per-second
                :eta eta}
     ;; what gets passed back in to the next progress-stats call
     :context {:start-time (or start-time now)
               :start-bytes (or start-bytes bytes)
               :fileset-file-start fileset-file-start}}))

(defn progress-bar-from-stats [host-string max-host-string-len max-filename-len
                               {:keys [file bytes total frac bytes-per-second eta]}]
  (let [
        columns (SpireUtils/get_terminal_width)
        right-side-buffer 32
        width (- columns right-side-buffer max-host-string-len max-filename-len 1)
        host-string-padding (apply str (map (fn [_] " ") (range (- max-host-string-len (count host-string)))))
        filename-padding (apply str (map (fn [_] " ") (range (- max-filename-len (count (str (content-display-name file)))))))
        percent (int (* 100 frac))
        num-chars (int (* width frac))
        num-spaces (- width num-chars)
        ;;bar (str (reverse-text true) (apply str (take num-chars (repeat " "))) (reverse-text false))
        bar (str (apply str (take num-chars (repeat "="))))
        spaces (apply str (take num-spaces (repeat " ")))

        line-str (str "|" bar spaces "| " percent "% "
                      (when bytes-per-second
                        (speed-string bytes-per-second))
                      (when eta
                        (str " eta:" (eta-string eta))))
        line-len (count line-str)
        eraser (apply str (take (- columns line-len max-host-string-len max-filename-len 1) (repeat " ")))]
    (str host-string  host-string-padding " " (content-display-name file) filename-padding line-str eraser)))

(defn strip-colour-codes [s]
  (string/replace s #"\033\[\d+m" "")
  )

(defn displayed-length
  "Guess how long a line will be when printed (ignore colour commands)"
  [s]
  (count (strip-colour-codes s)))

(defn n-spaces [n]
  (apply str (map (fn [_] " ") (range n))))

(defn erase-line []
  (n-spaces (SpireUtils/get_terminal_width)))

(defn append-erasure-to-line [s]
  (let [len (displayed-length s)
        term-width (SpireUtils/get_terminal_width)]
    (if (> term-width len)
      (str s (n-spaces (- term-width len)))
      (str (subs s 0 term-width) (colour 0)))))

(defn which-spire []
  (let [executable (executing-bin-path)
        java? (string/ends-with? executable "java")]
    (if java?
      (when (.exists (io/as-file "./spire")) "./spire")
      executable)))

;; (defn push
;;   [{{md5sum :md5sum} :paths} host-string runner session local-path remote-path]
;;   (let [run (fn [command]
;;                (let [{:keys [out exit]} (runner command "" "" {})]
;;                  (when (zero? exit)
;;                    (string/trim out))))
;;         local-md5 (digest/md5 (io/as-file local-path))
;;         remote-md5 (some-> (run (format "%s -b \"%s\"" md5sum remote-path))
;;                            (string/split #"\s+")
;;                            first)]
;;     ;; (println local-md5 remote-md5)
;;     ;; (println local-path remote-path)
;;     (when (or (not remote-md5) (not= local-md5 remote-md5))
;;       (println (format "Transfering %s to %s:%s" local-path host-string remote-path))
;;       (scp/scp-to session local-path remote-path :mode 0775 :progress-fn progress-bar)
;;       (println))))

(defn compatible-arch? [{{:keys [processor]} :arch}]
  (let [local-processor-arch (string/trim (:out (shell/sh "uname" "-p")))]
    (= local-processor-arch processor)))

(defmacro embed [filename]
  (slurp filename))

#_ (defmacro embed-src [fname]
  (slurp (io/file "src/clj" (.getParent (io/file *file*)) fname)))

(defmacro embed-src [fname]
  (slurp
   (let [f (io/file *file*)
         p (.getParent f)]
     (if (= (.getPath f) (.getAbsolutePath f))
       (io/file p fname)
       (io/file "src/clj" p fname)))))

(defmacro make-script [fname vars]
  `(str
    (apply str (for [[k# v#] ~vars] (str (name k#) "=\"" v# "\"\n")))
    (embed-src ~fname)))

(defn re-pattern-to-sed [re]
  (-> re
      .pattern
      (string/replace "\"" "\"")
      (string/replace "/" "\\/")
      (str "/")
      (->> (str "/"))))

(defn path-escape [path]
  (string/replace path "\"" "\\\""))

(defn double-quote [string]
  (str "\"" string "\""))

(defn path-quote [path]
  (double-quote (path-escape path)))

(defmacro defmodule [name module-args pipeline-args & body]
  `(defn ~name [& args#]
     (binding [spire.state/*form* (concat '(~name) args#)]
       (spire.output/print-form spire.state/*form*)
       (let [~module-args args#
             ~pipeline-args [spire.state/*host-string* spire.state/*connection*]
             result# (do ~@body)]
         (output/print-result result# spire.state/*host-string*)
         result#
         ))))


#_ (content-size (byte-array [1 2]))

#_ (content-file? (io/file "./spire"))


(defn relativise
  "return the relative path that gets you from a working directory
  `source` to the file or directory `target`"
  [source target]
  (let [source-path (Paths/get (.toURI (io/as-file (.getCanonicalPath (io/as-file source)))))
        target-path (Paths/get (.toURI (io/as-file (.getCanonicalPath (io/as-file target)))))]
    (-> source-path
        (.relativize target-path)
        .toFile
        .getPath)))



(def empty-file-attribute-array
  (make-array FileAttribute 0))

(def empty-link-options
  (make-array LinkOption 0))

(def no-follow-links
  (into-array LinkOption [LinkOption/NOFOLLOW_LINKS]))

(defn last-access-time [file]
  (let [p (.toPath (io/file file))]
    (/ (.toMillis (.lastAccessTime (Files/readAttributes p  java.nio.file.attribute.BasicFileAttributes empty-link-options)))
       1000)))

#_ (last-access-time ".")

(defn last-modified-time [file]
  (let [p (.toPath (io/file file))]
    (/ (.toMillis (.lastModifiedTime (Files/readAttributes p  java.nio.file.attribute.BasicFileAttributes empty-link-options)))
       1000)))

#_ (last-modified-time ".")

(def permission->mode
  {PosixFilePermission/OWNER_READ     0400
   PosixFilePermission/OWNER_WRITE    0200
   PosixFilePermission/OWNER_EXECUTE  0100
   PosixFilePermission/GROUP_READ     0040
   PosixFilePermission/GROUP_WRITE    0020
   PosixFilePermission/GROUP_EXECUTE  0010
   PosixFilePermission/OTHERS_READ    0004
   PosixFilePermission/OTHERS_WRITE   0002
   PosixFilePermission/OTHERS_EXECUTE 0001})

(defn file-mode [file]
  (let [p (.toPath (io/file file))
        perm-hash-set (.permissions (Files/readAttributes p  java.nio.file.attribute.PosixFileAttributes empty-link-options))]
    (reduce (fn [acc [perm-mode perm-val]]
              (if (.contains perm-hash-set perm-mode)
                (bit-or acc perm-val)
                acc))
            0 permission->mode)))

#_ (format "%o" (file-mode "."))

(defn mode->permissions [mode]
  (reduce (fn [acc [perm flag]]
            (if (pos? (bit-and mode flag))
              (conj acc perm)
              acc))
          #{} permission->mode))

#_ (mode->permissions 0700)

(defn set-file-mode [file mode]
  (let [p (.toPath (io/file file))]
    (Files/setPosixFilePermissions p (mode->permissions mode))))

#_ (set-file-mode "foo" 0644)

(defn create-file [file mode]
  (let [p (.toPath (io/file file))]
    (Files/createFile p (into-array FileAttribute [(PosixFilePermissions/asFileAttribute
                                                    (mode->permissions mode))]))))

#_ (create-file "foo" 0755)

(defn timestamp->touch [ts]
  (let [datetime (coerce/from-epoch ts)
        year (time/year datetime)
        month (time/month datetime)
        day (time/day datetime)
        hour (time/hour datetime)
        minute (time/minute datetime)
        second (time/second datetime)
        ]
    (format "%d-%02d-%02d %02d:%02d:%02d.000000000 +0000" year month day hour minute second)
    )
  )

#_ (timestamp->touch 1514779200)

(defn set-last-modified-time [file ts]
  (let [file-time (FileTime/fromMillis (* ts 1000))
        p (.toPath (io/file file))]
    (Files/setLastModifiedTime p file-time)))

#_ (set-last-modified-time "foo" 0)

(defn set-last-access-time [file ts]
  (let [file-time (FileTime/fromMillis (* ts 1000))
        p (.toPath (io/file file))]
    (.setTimes (Files/getFileAttributeView p BasicFileAttributeView empty-link-options)
               ;; modified access create
               nil file-time nil
               )))

#_ (set-last-access-time "foo" 0)

(defn set-last-modified-and-access-time [file modified access]
  (let [modified-time (FileTime/fromMillis (* modified 1000))
        access-time (FileTime/fromMillis (* access 1000))
        p (.toPath (io/file file))]
    (.setTimes (Files/getFileAttributeView p BasicFileAttributeView empty-link-options)
               ;; modified access create
               modified-time access-time nil)))

#_ (set-last-modified-and-access-time "foo" 99999 99999)
