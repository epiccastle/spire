(ns spire.core
  (:require [spire.shell :as sh]
            [puget.printer :as puget]
            [digest :as digest]
            [clj-time.core :as time]
            [clojure.string :as string]
            [clojure.java.io :as io])
  (:gen-class))

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
      (str s "s")

      (zero? h)
      (str m "m" s "s")

      :else
      (str h "h" m "m" s "s"))))

(defn progress-bar [bytes total frac {:keys [start-time start-bytes]}]
  (let [
        now (time/now)
        first? (not start-time)
        width 80
        percent (int (* width frac))
        num-chars (int (* width frac))
        num-spaces (- width num-chars)
        bar (apply str (take num-chars (repeat "=")))
        spaces (apply str (take num-spaces (repeat " ")))
        duration (when-not first? (time/in-seconds (time/interval start-time now)))
        bytes-since-start (when-not first? (- bytes start-bytes))
        bytes-per-second (when (some-> duration pos?) (int (/ bytes-since-start duration)))
        bytes-remaining (- total bytes)
        eta (when (and bytes-remaining bytes-per-second)
              (int (/ bytes-remaining bytes-per-second)))
        ]
    (.write *out* (str "\r|" bar spaces "| " percent "% "
                       (when bytes-per-second
                         (speed-string bytes-per-second))
                       (when eta
                         (str " eta:" (eta-string eta)))

                       "   "))
    (.flush *out*)
    {:start-time (or start-time now)
     :start-bytes (or start-bytes bytes)}
    ))

(defn -main
  "ssh to ourselves and collect paths"
  [& args]
  (let [host-string (or (first args) "localhost")
        proc (sh/proc ["ssh" host-string])
        run (fn [command]
              (let [{:keys [out exit]} (sh/run proc command)]
                (when (zero? exit)
                  (string/trim out))))
        which #(run (str "which " %))
        paths
        {:md5sum (which "md5sum")
         :crc32 (which "crc32")
         :sha1sum (which "sha1sum")
         :sha256sum (which "sha256sum")
         :sha512sum (which "sha512sum")
         :sha224sum (which "sha224sum")
         :sha384sum (which "sha384sum")
         :curl (which "curl")
         :wget (which "wget")
         :apt (which "apt")
         :apt-get (which "apt-get")
         :apt-cache (which "apt-cache")
         :rpm (which "rpm")
         :yum (which "yum")
         }

        lsb-release (some-> "lsb_release -a" run lsb-process)
        spire-md5 (digest/md5 (io/as-file "./spire"))
        remote-path (str "/tmp/spire-" spire-md5)
        spire-remote (some-> (run (str "md5sum -b " remote-path))
                             (string/split #"\s+")
                             first)
        properties  (into {} (map (fn [[k v]] [(keyword k) v]) (System/getProperties)))
        ]
    (when (not spire-remote)
      (sh/copy-with-progress "./spire" host-string remote-path progress-bar)
      (println)
      )

    (puget/cprint {:spire-local spire-md5
                   :spire-remote spire-remote
                   :paths paths
                   :lsb-release lsb-release
                   :properties properties})
    ))
