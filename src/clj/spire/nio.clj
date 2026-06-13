(ns spire.nio
  (:require [babashka.fs :as fs]
            [digest :as digest]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell])
  (:import [java.time Instant ZoneId ZonedDateTime]
           [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)

(defn relativise
  "return the relative path that gets you from a working directory
  `source` to the file or directory `target`"
  [source target]
  (fs/relativize source target))

(def empty-file-attribute-array [ ])

(def empty-link-options [])

(def no-follow-links [])

(defn last-access-time
  "return the last access time for the passed in file in seconds since the epoch.
  `file` is a string.
  "
  [file]
  (int (/ (.toMillis ^java.nio.file.attribute.FileTime (fs/last-modified-time file)) 1000)))

(defn last-modified-time
  "return the last modified time for the passed in file in seconds since the epoch.
  `file` is a string.
  "
  [file]
  (int (/ (.toMillis ^java.nio.file.attribute.FileTime (fs/last-modified-time file)) 1000)))

#_ (last-modified-time ".")

(defn file-mode
  "returns the modification bits of the file as an integer. If you express this in octal
  you will get the representation chmod command uses. eg `(format \"%o\" (file-mode \".\"))` "
  [file]
  (let [perms (fs/posix-file-permissions file)]
    (reduce (fn [acc [perm-key perm-val]]
              (if (contains? perms perm-key)
                (bit-or acc perm-val)
                acc))
            0
            {:owner-read 0400
             :owner-write 0200
             :owner-execute 0100
             :group-read 0040
             :group-write 0020
             :group-execute 0010
             :others-read 0004
             :others-write 0002
             :others-execute 0001})))

(defn mode->permissions
  "given an integer file mode, returns the set for babashka.fs use."
  [mode]
  (reduce (fn [acc [perm flag]]
            (if (pos? (bit-and mode flag))
              (conj acc perm)
              acc))
          #{}
          {:owner-read 0400
           :owner-write 0200
           :owner-execute 0100
           :group-read 0040
           :group-write 0020
           :group-execute 0010
           :others-read 0004
           :others-write 0002
           :others-execute 0001}))

(defn set-file-mode
  "set an existing `file` to the specified `mode`. `file` is a string. `mode` is an integer."
  [file mode]
  (fs/set-posix-file-permissions file (mode->permissions mode))
  (str file))

(defn create-file
  "create a new empty `file` with specified `mode`. Honours the umask."
  [file mode]
  (fs/create-file file {:posix-file-permissions (mode->permissions mode)})
  (str file))

(defn timestamp->touch
  "converts an integer timestamp to the format used by GNU touch"
  [ts]
  (let [instant (Instant/ofEpochSecond (int ts))
        dt (.atZone ^Instant instant (ZoneId/of "UTC"))
        fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss.SSSSSSSSS Z")]
    (.format ^ZonedDateTime dt fmt)))

(defn timestamp->touch-bsd
  "converts an integer timestamp to the format used by BSD touch"
  [ts]
  (let [instant (Instant/ofEpochSecond (long ts))
        dt (.atZone ^Instant instant (ZoneId/of "UTC"))
        fmt (DateTimeFormatter/ofPattern "yyyyMMddHHmm.ss")]
    (.format ^ZonedDateTime dt fmt)))

#_ (timestamp->touch 1514779200)

(defn set-last-modified-time
  "sets the last modified time of `file` to the timestamp `ts`"
  [file ts]
  (fs/set-last-modified-time file (fs/instant->file-time (java.time.Instant/ofEpochSecond ts)))
  (str file))

(defn set-last-access-time
  "sets the last access time of `file` to the timestamp `ts`"
  [file ts]
  (fs/set-attribute file "lastAccessTime" (fs/instant->file-time (java.time.Instant/ofEpochSecond ts))))

(defn set-last-modified-and-access-time
  "sets the last modified time of `file` to the timestamp `modified` and the last
  access time to the timestamp `access`"
  [file modified access]
  (fs/set-last-modified-time file (fs/instant->file-time (java.time.Instant/ofEpochSecond modified)))
  (fs/set-attribute file "lastAccessTime" (fs/instant->file-time (java.time.Instant/ofEpochSecond access))))

(defn idem-set-last-access-time
  "idempotently set the last access time for file `f`. Returns `true` if the file was changed,
  returns `nil` if the access time was already set."
  [f ts]
  (when (not= (last-access-time f) ts)
    (set-last-access-time f ts)
    true))

(defn idem-set-last-modified-time
  "idempotently set the last modified time for file `f`. Returns `true` if the file was changed,
  returns `nil` if the modified time was already set."
  [f ts]
  (when (not= (last-modified-time f) ts)
    (set-last-modified-time f ts)
    true))

(defmulti set-owner (fn [path owner] (type owner)))

(defmethod set-owner String [path owner]
  (let [{:keys [out err exit]} (shell/sh "chown" owner path)]
    (assert (= 0 exit))
    true))

(defmethod set-owner Long [path owner]
  (let [{:keys [out err exit]} (shell/sh "chown" (str owner) path)]
    (assert (= 0 exit))
    true))

(defn idem-set-owner
  "idempotently sets the owner of a file. returns true if the owner is actually changed."
  [file owner]
  (if (number? owner)
    (let [uid (fs/get-attribute file "unix:uid")]
      (when (not= owner uid) (set-owner file owner)))
    (let [user (fs/get-attribute file "unix:owner")]
      (when (not= owner (str user)) (set-owner file owner)))))

(defmulti set-group (fn [path owner] (type owner)))

(defmethod set-group String [path group]
  (let [{:keys [out err exit]} (shell/sh "chgrp" group path)]
    (assert (= 0 exit))
    true))

(defmethod set-group Long [path group]
  (let [{:keys [out err exit]} (shell/sh "chgrp" (str group) path)]
    (assert (= 0 exit))
    true))

(defn idem-set-group [file group]
  (if (number? group)
    (let [gid (fs/get-attribute file "unix:gid")]
      (when (not= group gid) (set-group file group)))
    (let [group-name (fs/get-attribute file "unix:group")]
      (when (not= group (str group-name)) (set-group file group)))))

#_ (idem-set-group "foo" "crispin")

(defn idem-set-mode [file mode]
  (if (= mode (file-mode file))
    false
    (do
      (set-file-mode file mode)
      true)))

(defn set-attr [file owner group mode]
  (let [p (.toPath (io/file file))]
    (let [o (when owner (idem-set-owner file owner))
          g (when group (idem-set-group file group))
          m (when mode (idem-set-mode file mode))]
      (or o g m))))

(defn set-attrs [{:keys [path owner group mode dir-mode attrs recurse]}]
  (let [file-path (io/file path)]
    (loop [[file & remain] (if recurse
                             (file-seq file-path)
                             [file-path])
           changed? false]
      (if file
        (cond
          (fs/directory? file)
          (recur remain (set-attr file owner group dir-mode))

          (fs/regular-file? file)
          (recur remain (set-attr file owner group mode))

          :else
          changed?)

        changed?))))

#_ (set-attrs {:path "foo" :mode 0644})

(defn set-attrs-preserve [remote-info dest]
  ;;(println ">>" remote-info dest)
  (let [path (io/file dest)]
    (loop [[file & remain] (file-seq path)
           changed? false]
      (if file
        (let [f (relativise dest file)
              {:keys [mode last-access last-modified] :as stats} (remote-info f)
              ]
          ;;(println f "->" stats)

          (if stats
            (recur
             remain
             (let [m (idem-set-mode file mode)
                   la (idem-set-last-access-time file last-access)
                   lm (idem-set-last-modified-time file last-modified)]
               (or m la lm)))

            ;; directory. TODO gather these in compare routines
            (recur remain changed?)))

        changed?)
      )
    )
  )
