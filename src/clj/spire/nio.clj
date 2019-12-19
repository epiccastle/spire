(ns spire.nio
  (:require ;;[spire.scp :as scp]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [digest :as digest]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell])
  (:import [java.nio.file Paths Files LinkOption Path FileSystems]
           [java.nio.file.attribute FileAttribute BasicFileAttributes BasicFileAttributeView
            PosixFilePermission PosixFilePermissions PosixFileAttributeView
            FileTime]))

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

(defmulti set-owner (fn [path owner] (type owner)))

(defmethod set-owner String [path owner]
  (let [p (.toPath (io/file path))
        fs (FileSystems/getDefault)
        upls (.getUserPrincipalLookupService fs)
        new-owner (.lookupPrincipalByName upls owner)]
    (Files/setOwner p new-owner)
    true))

(defmethod set-owner Long [path owner]
  (let [{:keys [out err exit]} (shell/sh "chown" (str owner) path)]
    (assert (= 0 exit))
    true))

#_ (set-owner "foo" "crispin")
#_ (set-owner "foo" 1000)

(defn idem-set-owner [file owner]
  (let [p (.toPath (io/file file))]
    (if (number? owner)
      (let [uid (Files/getAttribute p "unix:uid" empty-link-options)]
        (when (not= owner uid) (set-owner file owner)))
      (let [user (Files/getAttribute p "unix:owner" empty-link-options)]
        (when (not= owner (str user)) (set-owner file owner))))))

#_ (idem-set-owner "foo" "crispin")

;;
;; set file/directory groups
;;
(defmulti set-group (fn [path owner] (type owner)))

(defmethod set-group String [path group]
  (let [p (.toPath (io/file path))
        fs (FileSystems/getDefault)
        upls (.getUserPrincipalLookupService fs)
        new-group (.lookupPrincipalByGroupName upls group)]
    (-> (Files/getFileAttributeView p PosixFileAttributeView empty-link-options)
        (.setGroup new-group))
    true))

(defmethod set-group Long [path group]
  (let [{:keys [out err exit]} (shell/sh "chgrp" (str group) path)]
    (assert (= 0 exit))
    true))

#_ (set-group "foo" 1000)

(defn idem-set-group [file group]
  (let [p (.toPath (io/file file))]
    (if (number? group)
      (let [gid (Files/getAttribute p "unix:gid" empty-link-options)]
        (when (not= group gid) (set-group file group)))
      (let [group-name (Files/getAttribute p "unix:group" empty-link-options)]
        (when (not= group (str group-name)) (set-group file group-name))))))

#_ (idem-set-group "foo" "crispin")

(defn idem-set-mode [file mode]
  (if (= mode (file-mode file))
    false
    (do
      (set-file-mode file mode)
      true)))

(defn set-attr [file owner group mode]
  (let [p (.toPath (io/file file))]
    (or (when owner (idem-set-owner file owner))
        (when group (idem-set-group file group))
        (when mode (idem-set-mode file mode)))))

(defn set-attrs [{:keys [path owner group mode dir-mode attrs recurse]}]
  (let [file-path (io/file path)]
    (loop [[file & remain] (file-seq file-path)
           changed? false]
      (if file
        (cond
          (.isDirectory file)
          (recur remain (set-attr file owner group dir-mode))

          (.isFile file)
          (recur remain (set-attr file owner group mode))

          :else
          changed?)

        changed?))))

#_ (set-attrs {:path "foo" :mode 0666})
