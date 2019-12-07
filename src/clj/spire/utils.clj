(ns spire.utils
  (:require [spire.scp :as scp]
            [clj-time.core :as time]
            [digest :as digest]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]))

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
    (.write *out* (str line-str eraser))
    (.flush *out*)
    {:start-time (or start-time now)
     :start-bytes (or start-bytes bytes)}))

(defn progress-stats [bytes total frac {:keys [start-time start-bytes]}]
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
    {:progress {:bytes bytes
                :total total
                :frac frac
                :bytes-per-second bytes-per-second
                :eta eta}
     :context {:start-time (or start-time now)
               :start-bytes (or start-bytes bytes)}}))

(defn progress-bar-from-stats [host-string max-len {:keys [bytes total frac bytes-per-second eta]}]
  (let [
        columns (SpireUtils/get_terminal_width)
        right-side-buffer 32
        width (- columns right-side-buffer max-len)
        host-string-padding (apply str (map (fn [_] " ") (range (- max-len (count host-string)))))
        percent (int (* 100 frac))
        num-chars (int (* width frac))
        num-spaces (- width num-chars)
        bar (apply str (take num-chars (repeat "=")))
        spaces (apply str (take num-spaces (repeat " ")))

        line-str (str "|" bar spaces "| " percent "% "
                      (when bytes-per-second
                        (speed-string bytes-per-second))
                      (when eta
                        (str " eta:" (eta-string eta))))
        line-len (count line-str)
        eraser (apply str (take (- columns line-len max-len 1) (repeat " ")))]
    (str host-string host-string-padding line-str eraser)))

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

(defn push
  [{{md5sum :md5sum} :paths} host-string runner session local-path remote-path]
  (let [run (fn [command]
               (let [{:keys [out exit]} (runner command "" "" {})]
                 (when (zero? exit)
                   (string/trim out))))
        local-md5 (digest/md5 (io/as-file local-path))
        remote-md5 (some-> (run (format "%s -b \"%s\"" md5sum remote-path))
                           (string/split #"\s+")
                           first)]
    ;; (println local-md5 remote-md5)
    ;; (println local-path remote-path)
    (when (or (not remote-md5) (not= local-md5 remote-md5))
      (println (format "Transfering %s to %s:%s" local-path host-string remote-path))
      (scp/scp-to session local-path remote-path :mode 0775 :progress-fn progress-bar)
      (println))))

(defn compatible-arch? [{{:keys [processor]} :arch}]
  (let [local-processor-arch (string/trim (:out (shell/sh "uname" "-p")))]
    (= local-processor-arch processor)))

(defmacro embed [filename]
  (slurp filename))

(defmacro embed-src [fname]
  (slurp (io/file "src/clj" (.getParent (io/file *file*)) fname)))

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
       (let [~module-args args#]
         (spire.transport/pipelines
          (fn ~pipeline-args
            ~@body))))))
