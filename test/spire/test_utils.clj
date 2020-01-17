(ns spire.test-utils
  (:require
   [clojure.java.shell :as shell]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [spire.ssh :as ssh]
   ))



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
              (finally (remove-file ~sym))))
      `(let [~sym (create-temp-file-name)]
         (try
           (with-temp-file-names ~(subvec syms 1) ~@body)
           (finally (remove-file ~sym)))))))

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
        (ssh/ssh-exec spire.state/*connection* cmd "" "UTF-8" {})]
    (assert (zero? exit) (str "remote command '" cmd "' failed. err:" err))
    out))

(defn makedirs [path]
  (.mkdirs (io/file path)))

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
    (format "stat -f '%s'" (string/join " " (map stat-linux->bsd linux-args)))))
