(ns spire.module.stat
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.facts :as facts]
            [clojure.string :as string]))

(defn preflight [path]
  (facts/check-bins-present #{:stat}))

(defn make-script [path]
  (str "stat -c '%a\t%b\t%B\t%d\t%f\t%F\t%g\t%G\t%h\t%i\t%m\t%n\t%N\t%o\t%s\t%t\t%T\t%u\t%U\t%W\t%X\t%Y\t%Z' " (utils/path-escape path))
  #_ (str "stat -c '%a %b %c %d %f %i %l %n %s %S %t %T' " (utils/path-escape path))
  )

(defn split-and-process-out [out]
  (let [[access blocks block-size device mode file-type
         group-id group hard-links inode-number mount-point
         file-name quoted-file-name optimal-io size
         device-major device-minor user-id user create-time
         access-time mod-time status-time] (string/split (string/trim out) #"\t")]
    {:access (Integer/parseInt access 8)
     :blocks (Integer/parseInt blocks)
     :block-size (Integer/parseInt block-size)
     :device (Integer/parseInt device)
     :mode (Integer/parseInt mode 16)
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
     :create-time (Integer/parseInt create-time)
     :access-time (Integer/parseInt access-time)
     :mod-time (Integer/parseInt mod-time)
     :status-time (Integer/parseInt status-time)
     }
    )
  )

(defn process-result [path {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    (assoc result
           :stat (split-and-process-out out)
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
   (->> (ssh/ssh-exec session (make-script path) "" "UTF-8" {})
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
