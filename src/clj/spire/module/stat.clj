(ns spire.module.stat
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.facts :as facts]
            [clojure.string :as string])
  (:import [java.util Date]))

(defn preflight [path]
  (facts/check-bins-present #{:stat}))

(defn make-script [path]
  (str "stat -c '%a\t%b\t%B\t%d\t%f\t%F\t%g\t%G\t%h\t%i\t%m\t%n\t%N\t%o\t%s\t%t\t%T\t%u\t%U\t%W\t%X\t%Y\t%Z' " (utils/path-escape path) "\n"
       "stat -f -c '%a\t%b\t%c\t%d\t%f\t%i\t%l\t%s\t%S\t%t\t%T' " (utils/path-escape path)))

(defn make-script-bsd [path]
  (str "stat -f '%Lp ' " (utils/path-escape path)))

(defn- epoch-string->inst [s]
  (-> s Integer/parseInt (* 1000) Date.))

(defn split-and-process-out-bsd [out]
  (let [parts (-> out string/trim (string/split #"\s+"))
        [mode] parts]
    {:mode (Integer/parseInt mode 8)}
    )
  )

(defn split-and-process-out [out]
  (let [[line1 line2] (string/split (string/trim out) #"\n")
        [mode blocks block-size device raw-mode file-type
         group-id group hard-links inode-number mount-point
         file-name quoted-file-name optimal-io size
         device-major device-minor user-id user create-time
         access-time mod-time status-time] (string/split line1 #"\t")
        [user-blocks-free blocks-total nodes-total nodes-free
         blocks-free file-system-id filename-max-len block-size-2
         block-size-fundamental filesystem-type filesystem-type-2] (string/split line2 #"\t")
        ]
    {:mode (Integer/parseInt mode 8)
     :blocks (Integer/parseInt blocks)
     :block-size (Integer/parseInt block-size)
     :device (Integer/parseInt device)
     :raw-mode (Integer/parseInt raw-mode 16)
     :file-type file-type
     :group-id (Integer/parseInt group-id)
     :group group
     :hard-links (Integer/parseInt hard-links)
     :inode-number (Integer/parseInt inode-number)
     :mount-point mount-point
     :file-name file-name
     :quoted-file-name quoted-file-name
     :optimal-io (Integer/parseInt optimal-io)
     :size (Integer/parseInt size)
     :device-major (Integer/parseInt device-major)
     :device-minor (Integer/parseInt device-minor)
     :user-id (Integer/parseInt user-id)
     :user user
     :create-time (when-not (= "0" create-time)
                    (epoch-string->inst create-time)) ;; on linux 0 means "unknown"
     :access-time (epoch-string->inst access-time)
     :mod-time (epoch-string->inst mod-time)
     :status-time (epoch-string->inst status-time)
     :filesystem {
                  :user-blocks-free (Integer/parseInt user-blocks-free)
                  :blocks-total (Integer/parseInt blocks-total)
                  :nodes-total (Integer/parseInt nodes-total)
                  :nodes-free (Integer/parseInt nodes-free)
                  :blocks-free (Integer/parseInt blocks-free)
                  :file-system-id file-system-id
                  :filename-max-len (Integer/parseInt filename-max-len)
                  :block-size-2 (Integer/parseInt block-size-2)
                  :block-size-fundamental (Integer/parseInt block-size-fundamental)
                  :filesystem-type (Integer/parseInt filesystem-type 16)
                  :filesystem-type-hex filesystem-type
                  :filesystem-type-2 filesystem-type-2
                  }}))

(defn process-result [path {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    (assoc result
           :stat (facts/on-os :linux (split-and-process-out out)
                              :else (split-and-process-out-bsd out))
           :result :ok
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )

    (= 255 exit)
    (assoc result
           :result :changed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )

    :else
    (assoc result
           :result :failed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")))
  )

(utils/defmodule stat* [path]
  [host-config session]
  (or
   (preflight path)
   (->> (ssh/ssh-exec session (facts/on-os :linux (make-script path)
                                           :else (make-script-bsd path)) "" "UTF-8" {})
        (process-result path))))

(defmacro stat [& args]
  `(utils/wrap-report ~*file* ~&form (stat* ~@args)))

(def documentation
  {
   :module "stat"
   :blurb "Retrieve file status"
   :description
   ["This module runs the stat command on files or directories."]
   :form "(stat path)"
   :args
   [{:arg "path"
     :desc "The path of the file or directory to stat."}]

   :examples
   [
    {:description
     "Stat a file."
     :form "
(stat \"/etc/resolv.conf\")"}]})
