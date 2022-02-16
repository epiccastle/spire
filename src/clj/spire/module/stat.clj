(ns spire.module.stat
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.facts :as facts]
            [clojure.string :as string]
            [clojure.edn :as edn])
  (:import [java.util Date]))

(defn preflight [path]
  (facts/check-bins-present #{:stat}))

(defn make-script [path]
  (str "stat -c '%a\t%b\t%B\t%d\t%f\t%F\t%g\t%G\t%h\t%i\t%m\t%n\t%N\t%o\t%s\t%t\t%T\t%u\t%U\t%W\t%X\t%Y\t%Z\t%F' " (utils/path-quote path)))

(defn make-script-bsd [path]
  (str "stat -f '%Lp%t%d%t%i%t%l%t%u%t%g%t%r%t%a%t%m%t%c%t%B%t%z%t%b%t%k%t%f%t%v%t%HT%t%N%t%Y%t%Hr%t%Lr' " (utils/path-quote path)))

(defn- epoch-string->inst [s]
  (-> s Integer/parseInt (* 1000) Date.))

(def bsd-file-types
  {
   "Directory" :directory
   "Block Device" :block-device
   "Character Device" :char-device
   "Symbolic Link" :symlink
   "Fifo File" :fifo
   "Regular File" :regular-file
   "Socket" :socket
   })

(defn split-and-process-out-bsd [out]
  (let [parts (-> out string/trim (string/split #"\t"))
        [mode device inode nlink uid gid rdev
         atime mtime ctime btime filesize blocks
         blksize flags gen file-type link-source link-dest
         device-major device-minor] parts
        file-type (bsd-file-types file-type)
        result {:mode (Integer/parseInt mode 8)
                :device (edn/read-string device)
                :inode (Integer/parseInt inode)
                :nlink (Integer/parseInt nlink)
                :uid (Integer/parseInt uid)
                :gid (Integer/parseInt gid)
                :rdev (edn/read-string rdev)
                :atime (epoch-string->inst atime)
                :mtime (epoch-string->inst mtime)
                :ctime (epoch-string->inst ctime)
                :btime (epoch-string->inst btime)
                :size (Integer/parseInt filesize)
                :blocks (Integer/parseInt blocks)
                :blksize (Integer/parseInt blksize)
                :flags (Integer/parseInt flags)
                :gen (Integer/parseInt gen)
                :file-type file-type
                :device-major (edn/read-string device-major)
                :device-minor (edn/read-string device-minor)}]
    (if (= file-type :symlink)
      (assoc result
             :link-source link-source
             :link-dest link-dest)
      result)
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
              (apply str "a non valid character was found outside a quoted region: " c " : " r))
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

(def linux-file-types
  {
   "directory" :directory
   "block special file" :block-device
   "character special file" :char-device
   "symbolic link" :symlink
   "fifo" :fifo
   "regular file" :regular-file
   "socket" :socket
   })

(defn split-and-process-out [out]
  (let [[mode blocks blksize device raw-mode file-type
         gid group nlink inode mount-point
         file-name quoted-file-name optimal-io size
         device-major device-minor uid user ctime
         atime mtime stime file-type] (string/split (string/trim out) #"\t")
        {:keys [source dest]} (process-quoted-symlink-line quoted-file-name)
        file-type (linux-file-types file-type)
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
                :device-major (Integer/parseInt device-major)
                :device-minor (Integer/parseInt device-minor)}]
    (assert (= file-name source)
            (str "decoding of quoted stat filename mismatched: "
                 (prn-str file-name source)))

    (if (= file-type :symlink)
      (assoc result
             :link-source source
             :link-dest dest)
      result)))

(defn process-result [path {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    {:exit 0
     :err err
     :stat (facts/on-os :linux (split-and-process-out out)
                        :else (split-and-process-out-bsd out))
     :result :ok}

    :else
    (assoc result
           :result :failed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n"))))

(utils/defmodule stat* [path]
  [host-config session {:keys [exec-fn sudo] :as shell-context}]
  (let [script (facts/on-os :linux (make-script path)
                            :else (make-script-bsd path))]
    (or
     (preflight path)

     ;; sash (reported as sh) does not correctly return its exit code
     ;; $ sash -c "echo foo; exit 1"; echo $?
     ;; foo
     ;; 0
     ;; so we invoke bash in this case as a work around
     (->> (exec-fn session
                   (facts/on-shell
                    :sh "bash"
                    :else script)
                   (facts/on-shell
                    :sh script
                    :else "")
                   "UTF-8" {:sudo sudo})
          (process-result path)))))

(defmacro stat
  "runs the stat command on files or directories.
  (stat path)

  given:

  `path`: The path of the file or directory

  returns:

  a hashmap with keys:

  `:result` the execution result. `:ok` or `:failed`

  `:exit` the exit code of the stat call

  `:err` The content of standard error from the execution

  `:stat` A result hashmap with the keys (not every field supported on
  every platform): `:user` the username of the owner of the file or
  directory. `:group` the group name owning the file or
  directory. `:uid` the user id of the owner. `:gid` the group id of
  ownership. `:atime` the last access time. `:ctime` the creation
  time. `:mtime` the modification time. `:btime` the BSD
  btime. `:mode` the file access permission mode. `:inode` the disk
  inode number. `:size` the size of the file in bytes. `:device-minor`
  the minor device number for device nodes. `:nlink` the number of
  hard links pointing to the file. `:file-type` the type of
  file. `:blocks` the number of disk blocks it occupies. `:device` the
  device number. `:blksize` the size of blocks on the contianing
  device. `:flags` a list of file flags. `:device-major` the major dev
  node number. `:rdev` the rdev number. `:link-source` the source of a
  sumbolic link. `:link-dest` the destination of symbolic link.
  "
  [& args]
  `(utils/wrap-report ~&form (stat* ~@args)))

;;
;; Test mode flags
;;
(defn other-exec?
  "When passed the result of a `stat` call, returns `true` if others can
  execute the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 1)))

(defn other-write?
  "When passed the result of a `stat` call, returns `true` if others can
  write to the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 2)))

(defn other-read?
  "When passed the result of a `stat` call, returns `true` if others can
  read from the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 4)))

(defn group-exec?
  "When passed the result of a `stat` call, returns `true` if members of
  the file's group can execute the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 8)))

(defn group-write?
  "When passed the result of a `stat` call, returns `true` if members of
  the file's group can write to the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 16)))

(defn group-read?
  "When passed the result of a `stat` call, returns `true` if members of
  the file's group can read from the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 32)))

(defn user-exec?
  "When passed the result of a `stat` call, returns `true` if the owner
  can execute the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 64)))

(defn user-write?
  "When passed the result of a `stat` call, returns `true` if the owner
  can write to the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 128)))

(defn user-read?
  "When passed the result of a `stat` call, returns `true` if the owner
  can read from the file."
  [{{:keys [mode]} :stat}]
  (pos? (bit-and mode 256)))

(defn mode-flags
  "When passed the result of a `stat` call, returns a hashmap with the
  following keys:

  `:user-read` can the owner read from the file.
  `:user-write` can the owner write to the file.
  `:user-exec?` can the owner execute the file.
  `:group-read` can members of the file's group read from the file.
  `:group-write` can members of the file's group write to the file.
  `:group-exec?` can members of the file's group execute the file.
  `:other-read` can others read from the file.
  `:other-write` can others write to the file.
  `:other-exec?` can others execute the file.
  "
  [result]
  {:user-read? (user-read? result)
   :user-write? (user-write? result)
   :user-exec? (user-exec? result)
   :group-read? (group-read? result)
   :group-write? (group-write? result)
   :group-exec? (group-exec? result)
   :other-read? (other-read? result)
   :other-write? (other-write? result)
   :other-exec? (other-exec? result)})

(defn exec?
  "When given the result of a `stat` call, return `true` if you can
  execute the file with your present user and permissions."
  [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can execute any file that has any executable bit set
      (pos? (bit-and mode (bit-or 64 8 1)))

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 64)))
       (and (group-ids gid) (pos? (bit-and mode 8)))
       (pos? (bit-and mode 1))))))

(defn readable?
  "When given the result of a `stat` call, return `true` if you can
  read from the file with your present user and permissions."
  [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can read any file
      true

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 256)))
       (and (group-ids gid) (pos? (bit-and mode 32)))
       (pos? (bit-and mode 4))))))

(defn writeable?
  "When given the result of a `stat` call, return `true` if you can
  write to the file with your present user and permissions."
  [{{:keys [mode uid gid]} :stat}]
  (let [{{fact-uid :id} :uid group-ids :group-ids} (facts/get-fact [:user])]
    (if (zero? fact-uid)
      ;; root can write to any file
      true

      ;; check individual flags
      (or
       (and (= fact-uid uid) (pos? (bit-and mode 128)))
       (and (group-ids gid) (pos? (bit-and mode 16)))
       (pos? (bit-and mode 2))))))

;;
;; File types
;;
(defn directory?
  "When given the result of a `stat` call, return `true` if the path is
  a directory. "
  [{{:keys [file-type]} :stat}]
  (= :directory file-type))

(defn block-device?
  "When given the result of a `stat` call, return `true` if the path is
  a block device. "
  [{{:keys [file-type]} :stat}]
  (= :block-device file-type))

(defn char-device?
  "When given the result of a `stat` call, return `true` if the path is
  a character device. "
  [{{:keys [file-type]} :stat}]
  (= :char-device file-type))

(defn symlink?
  "When given the result of a `stat` call, return `true` if the path is
  a symlink. "
  [{{:keys [file-type]} :stat}]
  (= :symlink file-type))

(defn fifo?
  "When given the result of a `stat` call, return `true` if the path is
  a fifo. "
  [{{:keys [file-type]} :stat}]
  (= :fifo file-type))

(defn regular-file?
  "When given the result of a `stat` call, return `true` if the path is
  a regular file. "
  [{{:keys [file-type]} :stat}]
  (= :regular-file file-type))

(defn socket?
  "When given the result of a `stat` call, return `true` if the path is
  a unix domain socket. "
  [{{:keys [file-type]} :stat}]
  (= :socket file-type))


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

   :opts []

   :examples
   [
    {:description
     "Stat a file."
     :form "
(stat \"/etc/resolv.conf\")"}

    {:description
     "Test the return value of a stat call for a unix domain socket"
     :form "
(socket? (stat (System/getenv \"SSH_AUTH_SOCK\")))
"}]})
