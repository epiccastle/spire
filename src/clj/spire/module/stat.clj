(ns spire.module.stat
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.facts :as facts]
            [clojure.string :as string])
  (:import [java.util Date]))

(defn preflight [path]
  (facts/check-bins-present #{:stat}))

(defn make-script [path]
  (str "stat -c '%a\t%b\t%B\t%d\t%f\t%F\t%g\t%G\t%h\t%i\t%m\t%n\t%N\t%o\t%s\t%t\t%T\t%u\t%U\t%W\t%X\t%Y\t%Z\t%F' " (utils/path-quote path) "\n"
       #_ "stat -f -c '%a\t%b\t%c\t%d\t%f\t%i\t%l\t%s\t%S\t%t\t%T' "
       #_ (utils/path-escape path)))

(defn make-script-bsd [path]
  (str "stat -f '%Lp %d %i %l %u %g %r %a %m %c %B %z %b %k %f %v' " (utils/path-quote path)))

(defn- epoch-string->inst [s]
  (-> s Integer/parseInt (* 1000) Date.))

(defn split-and-process-out-bsd [out]
  (let [parts (-> out string/trim (string/split #"\s+"))
        [mode device inode nlink uid gid rdev
         atime mtime ctime btime filesize blocks
         blksize flags gen] parts]
    {:mode (Integer/parseInt mode 8)
     :device device
     :inode (Integer/parseInt inode)
     :nlink (Integer/parseInt nlink)
     :uid (Integer/parseInt uid)
     :gid (Integer/parseInt gid)
     :rdev rdev
     :atime (epoch-string->inst atime)
     :mtime (epoch-string->inst mtime)
     :ctime (epoch-string->inst ctime)
     :btime (epoch-string->inst btime)
     :size (Integer/parseInt filesize)
     :blocks (Integer/parseInt blocks)
     :blksize (Integer/parseInt blksize)
     :flags (Integer/parseInt flags)
     :gen (Integer/parseInt gen)
     }
    )
  )

(defn process-quoted-filename [quoted-line]
  (loop [[c & r] quoted-line
         acc ""]
    (case c
      \' (let [[r acc] (loop [[c & r] r
                              acc acc]
                         (assert c "ran out of chars while looking for close quote")
                         (case c
                           \' [r acc]
                           (recur r (str acc c))))]
           (recur r acc))
      \\ (recur (rest r) (str acc (first r)))
      \space [acc (apply str (conj r c))]
      nil (if c
            (recur r (str acc c))
            [acc nil])
      (assert false
              (apply str "a non valid character was found outside a quoted region: " c r))
      )))

(defn process-quoted-symlink-line [quoted-line]
  (let [[source remain] (process-quoted-filename quoted-line)]
    (if remain
      (do
        (assert (string/starts-with? remain " -> ") "malformed symlink line")
        (let [[dest remain] (process-quoted-filename (subs remain 4))]
          (assert (nil? remain) "malformed symlink line tail")
          {:source source
           :dest dest}))
      {:source source})))

#_ (process-quoted-symlink-line "'spire/spire-link'\\''f' -> 'foo'")

#_ (assert
    (= (process-quoted-filename "'spire/spire-link'\\''f' -> 'foo'")
       (process-quoted-filename "'spire/spire-link'\\''f'")
       "spire/spire-link'f"))

(defn split-and-process-out [out]
  (let [[line1 line2] (string/split (string/trim out) #"\n")
        [mode blocks blksize device raw-mode file-type
         gid group nlink inode mount-point
         file-name quoted-file-name optimal-io size
         device-major device-minor uid user ctime
         atime mtime stime file-type] (string/split line1 #"\t")
        ;;link-destination line2
        #_ [user-blocks-free blocks-total nodes-total nodes-free
            blocks-free file-system-id filename-max-len blksize-2
            blksize-fundamental filesystem-type filesystem-type-2]
        #_ (string/split line2 #"\t")
        {:keys [source dest]} (process-quoted-symlink-line quoted-file-name)

        result {:mode (Integer/parseInt mode 8)
                :device (Integer/parseInt device)
                :inode (Integer/parseInt inode)
                :nlink (Integer/parseInt nlink)
                :uid (Integer/parseInt uid)
                :user user
                :gid (Integer/parseInt gid)
                :group group
                :rdev nil
                :ctime (when-not (= "0" ctime)
                         (epoch-string->inst ctime)) ;; on linux 0 means "unknown"
                :atime (epoch-string->inst atime)
                :mtime (epoch-string->inst mtime)
                :btime nil
                :size (Integer/parseInt size)
                :blocks (Integer/parseInt blocks)
                :blksize (Integer/parseInt blksize)
                :flags nil
                :gen nil

                :file-type file-type

                ;; :link-source source
                ;; :link-dest dest

                ;; :raw-mode (Integer/parseInt raw-mode 16)
                ;; :file-type file-type

                ;; :group group


                ;; :mount-point mount-point
                ;; :file-name file-name
                ;; :quoted-file-name quoted-file-name
                ;; :link-dest link-destination
                ;; :optimal-io (Integer/parseInt optimal-io)

                :device-major (Integer/parseInt device-major)
                :device-minor (Integer/parseInt device-minor)

                ;; :user user

                ;; :status-time (epoch-string->inst status-time)
                ;; :filesystem {
                ;;              :user-blocks-free (Integer/parseInt user-blocks-free)
                ;;              :blocks-total (Integer/parseInt blocks-total)
                ;;              :nodes-total (Integer/parseInt nodes-total)
                ;;              :nodes-free (Integer/parseInt nodes-free)
                ;;              :blocks-free (Integer/parseInt blocks-free)
                ;;              :file-system-id file-system-id
                ;;              :filename-max-len (Integer/parseInt filename-max-len)
                ;;              :blksize-2 (Integer/parseInt blksize-2)
                ;;              :blksize-fundamental (Integer/parseInt blksize-fundamental)
                ;;              :filesystem-type (Integer/parseInt filesystem-type 16)
                ;;              :filesystem-type-hex filesystem-type
                ;;              :filesystem-type-2 filesystem-type-2}
                }]
    (assert (= file-name source)
            (str "decoding of quoted stat filename mismatched: "
                 (prn-str file-name source)))

    (if (= file-type "symbolic link")
      (assoc result
             :link-source source
             :link-dest dest)
      result)

    ))

(defn process-result [path {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    {:exit 0
     :err err
     :stat (facts/on-os :linux (split-and-process-out out)
                        :else (split-and-process-out-bsd out))
     :result :ok}

    ;; (= 255 exit)
    ;; (assoc result
    ;;        :result :changed
    ;;        :out-lines (string/split out #"\n")
    ;;        :err-lines (string/split err #"\n")
    ;;        )

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

;;
;; Test mode flags
;;
(defn other-exec? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 1)))

(defn other-write? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 2)))

(defn other-read? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 4)))

(defn group-exec? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 8)))

(defn group-write? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 16)))

(defn group-read? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 32)))

(defn user-exec? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 64)))

(defn user-write? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 128)))

(defn user-read? [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 256)))

(defn mode-flags [result]
  {:user-read? (user-read? result)
   :user-write? (user-write? result)
   :user-exec? (user-exec? result)
   :group-read? (group-read? result)
   :group-write? (group-write? result)
   :group-exec? (group-exec? result)
   :other-read? (other-read? result)
   :other-write? (other-write? result)
   :other-exec? (other-exec? result)})

(defn exec? [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can execute any file that has any executable bit set
      (pos? (bit-and mode (bit-or 64 8 1)))

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 64)))
       (and (group-ids gid) (pos? (bit-and mode 8)))
       (pos? (bit-and mode 1))))))

(defn readable? [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can read any file
      true

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 256)))
       (and (group-ids gid) (pos? (bit-and mode 32)))
       (pos? (bit-and mode 4))))))

(defn writeable? [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can write to any file
      true

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 128)))
       (and (group-ids gid) (pos? (bit-and mode 16)))
       (pos? (bit-and mode 2))))))



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
