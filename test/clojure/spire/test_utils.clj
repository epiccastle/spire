(ns spire.test-utils
  (:require
   [clojure.test :refer :all]
   [clojure.java.shell :as shell]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [spire.ssh :as ssh]
   [spire.state]
   )
  (:import [java.nio.file Files]))



(defn test-pipelines [func]
  (func "localhost" nil))

(defn test-ssh-exec [session command in out opts]
  ;;(println command)
  (shell/sh "bash" "-c" command :in in))

(defn test-scp-to [session local-paths remote-path & opts]
  ;; just handles a single file for now
  (io/copy (io/file local-paths) (io/file remote-path)))

(defmacro ^{:private true} assert-args
  [& pairs]
  `(do (when-not ~(first pairs)
         (throw (IllegalArgumentException.
                 (str (first ~'&form) " requires " ~(second pairs) " in " ~'*ns* ":" (:line (meta ~'&form))))))
       ~(let [more (nnext pairs)]
          (when more
            (list* `assert-args more)))))

(defmacro with-temp-files [bindings & body]
  (assert-args
   (vector? bindings) "a vector for its binding"
   (even? (count bindings)) "an even number of forms in binding vector")
  (let [[sym fname & remain] bindings]
    (if-not remain
      `(let [~sym (create-temp-file ~fname)]
         (try ~@body
              (finally (remove-file ~sym))))
      `(let [~sym (create-temp-file ~fname)]
         (try
           (with-temp-files ~(subvec bindings 2) ~@body)
           (finally (remove-file ~sym)))))))

#_ (macroexpand-1 '(with-temp-files [f "mytemp"] 1 2 3))
#_ (macroexpand-1 '(with-temp-files [f "mytemp" g "mytemp2"] 1 2 3))

(def tmp-dir "/tmp")

(defn rand-string [n]
  (apply str (map (fn [_] (rand-nth "abcdefghijklmnopqrztuvwxyz0123456789")) (range n))))

(defn create-temp-file [src]
  (let [tmp (io/file tmp-dir (str "spire-test-" (rand-string 8)))]
    (io/copy (io/file src) tmp)
    (.getPath tmp)))

(defn create-temp-file-name []
  (let [tmp (io/file tmp-dir (str "spire-test-" (rand-string 8)))]
    (.getPath tmp)))

#_ (create-temp-file "project.clj")

(defn delete-recursive [f]
  (let [f (io/file f)]
    (when (.isDirectory f)
      (doseq [path (.listFiles f)]
        (delete-recursive path)))
    (.delete f)))

(defn remove-file [tmp]
  (assert (string/starts-with? tmp tmp-dir))
  (delete-recursive tmp))

#_ (remove-file (create-temp-file "project.clj"))

#_ (with-temp-files [f "project.clj"] (+ 10 20))
#_ (with-temp-files [f "project.clj"] (+ 10 20) (throw (ex-info "foo" {})))

(defmacro with-temp-file-names [syms & body]
  (let [[sym & remain] syms]
    (if-not remain
      `(let [~sym (create-temp-file-name)]
         (try ~@body
              (finally (remove-file ~sym))
              ))
      `(let [~sym (create-temp-file-name)]
         (try
           (with-temp-file-names ~(subvec syms 1) ~@body)
           (finally (remove-file ~sym))
           )))))

(defn is-root? []
  (= "root" (System/getProperty "user.name")))

(defn last-line [f]
  (last (line-seq (io/reader f))))

(defn last-user []
  (let [[username password uid gid userinfo homedir]
        (-> "/etc/passwd"
            last-line
            (string/split #":")
            )]
    {:username username
     :password password
     :uid uid
     :gid gid
     :userinfo userinfo
     :homedir homedir}))

#_ (last-user)

(defn last-group []
  (let [[groupname _ gid]
        (-> "/etc/group"
            last-line
            (string/split #":"))]
    {:groupname groupname
     :gid gid}))

#_ (last-group)

(defn run [command]
  (let [{:keys [exit err out]} (shell/sh "bash" "-c" command)]
    (assert (zero? exit) (format "%s failed: %d\nerr: %s\nout: %s\n" command exit err out))
    out))

(defn uname []
  (string/trim (run "uname")))

;;
;; You can get the value directly using a stat output format, e.g. BSD/OS X:
;; stat -f "%OLp" <file>
;; or in Linux
;; stat --format '%a' <file>
;; and in busybox
;;  stat -c '%a' <file>
;;
(defn mode [f]
  (->
   (if (= "Linux" (uname))
     "stat -c '%%a' \"%s\""
     "stat -f '%%OLp' \"%s\"")
   (format f)
   run
   string/trim
   ))

#_ (mode "project.clj")

(defn ssh-run [cmd]
  (let [{:keys [out err exit]}
        (ssh/ssh-exec @spire.state/connection cmd "" "UTF-8" {})]
    (assert (zero? exit) (str "remote command '" cmd "' failed. err:" err))
    out))

(defn makedirs [path]
  (.mkdirs (io/file path)))

(defn ln-s [dest src]
  (run (format "ln -s '%s' '%s'" dest src)))

(def stat-linux->bsd
  {
   "%s" "%z"        ;; file size
   "%F" "%OMp"      ;; File type
   "%n" "%N"        ;; file name
   "%a" "%OLp"      ;; access rights
   "%X" "%a"        ;; last access seconds since epoch
   "%Y" "%m"        ;; last modified seconds since epoch
   })

(defn make-stat-command [linux-args]
  (if (= "Linux" (uname))
    (format "stat -c '%s'" (string/join " " linux-args))
    (format "stat -f '%s'" (string/join " " (map #(stat-linux->bsd % %) linux-args)))))

(defn make-md5-command []
  (if (= "Linux" (uname))
    "md5sum"
    "md5")
  )

;; often after an operation we want to check that files contain certain
;; contents, have certain flags and attributes, or match the contents
;; and attributes of other files. Following are some helper functions and
;; macros to assist test writing

(defn recurse-stat-match? [local-path remote-path stat-params]
  (= (run
       (format "cd \"%s\" && find . -exec %s {} \\;"
               local-path (make-stat-command stat-params)))
     (ssh-run
      (format "cd \"%s\" && find . -exec %s {} \\;"
              remote-path (make-stat-command stat-params)))))

(defn recurse-file-size-type-name-match?
  [local-path remote-path]
  (recurse-stat-match? local-path remote-path ["%s" "%F" "%n"])
  )

(defn recurse-file-size-type-name-mode-match?
  [local-path remote-path]
  (recurse-stat-match? local-path remote-path ["%s" "%F" "%n" "%a"])
  )

(defn recurse-access-modified-match?
  [local-path remote-path]
  (recurse-stat-match? local-path remote-path ["%X" "%Y"])
  )

(defn find-files-md5-match? [local remote]
  (= (run
       (format "cd \"%s\" && find . -type f -exec %s {} \\;"
               local
               (make-md5-command)))
     (ssh-run
      (format "cd \"%s\" && find . -type f -exec %s {} \\;"
              remote
              (make-md5-command)))))

(defn files-md5-match? [local remote]
  (if (= "Linux" (uname))
    (let [[local-md5 local-path]
          (string/split
           (run
             (format "md5sum \"%s\""
                     local))
           #" ")
          [remote-md5 remote-path]
          (string/split
           (ssh-run
            (format "md5sum \"%s\""
                    remote))
           #" ")]
      ;;(prn local-md5 remote-md5)
      (= local-md5 remote-md5))
    (let [[_ local-md5]
          (string/split
           (run
             (format "md5 \"%s\""
                     local))
           #"\s*=\s*")
          [_ remote-md5]
          (string/split
           (ssh-run
            (format "md5 \"%s\""
                    remote))
           #"\s*=\s*")]
      ;;(prn local-md5 remote-md5)
      (= local-md5 remote-md5))
    ))

(defn find-local-files-mode-is? [local mode-str]
  (let [modes (run
                (format "cd \"%s\" && find . -type f -exec %s {} \\;"
                        local
                        (make-stat-command ["%a"])))
        mode-lines (string/split-lines modes)
        ]
    (every? #(= mode-str %) mode-lines)))

(defn find-local-dirs-mode-is? [local mode-str]
  (let [modes (run
                (format "cd \"%s\" && find . -type d -exec %s {} \\;"
                        local
                        (make-stat-command ["%a"])))
        mode-lines (string/split-lines modes)
        ]
    (every? #(= mode-str %) mode-lines)))

(defn find-remote-files-mode-is? [local mode-str]
  (let [modes (ssh-run
                (format "cd \"%s\" && find . -type f -exec %s {} \\;"
                        local
                        (make-stat-command ["%a"])))
        mode-lines (string/split-lines modes)
        ]
    (every? #(= mode-str %) mode-lines)))

(defn find-remote-dirs-mode-is? [local mode-str]
  (let [modes (ssh-run
                (format "cd \"%s\" && find . -type d -exec %s {} \\;"
                        local
                        (make-stat-command ["%a"])))
        mode-lines (string/split-lines modes)
        ]
    (every? #(= mode-str %) mode-lines)))

(defn stat-local [local stat-params]
  (string/trim
   (run
     (format "%s \"%s\""
             (make-stat-command stat-params)
             local)))
  )

(defmacro should-ex-data [data & body]
  `(try ~@body
        (catch clojure.lang.ExceptionInfo e#
          (is (= ~data (ex-data e#))))))

(defn slurp-bytes
  "Slurp the bytes from a slurpable thing"
  [x]
  (with-open [out (java.io.ByteArrayOutputStream.)]
    (clojure.java.io/copy (clojure.java.io/input-stream x) out)
    (.toByteArray out)))

(defn spit-bytes
  "binary spit"
  [file bytes]
  (with-open [out (io/output-stream (io/file file))]
    (.write out bytes)))

(defn sym-link? [f]
  (Files/isSymbolicLink (.toPath f)))
