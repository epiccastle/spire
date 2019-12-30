(ns spire.facts
  (:require [clojure.string :as string]
            [spire.ssh :as ssh]
            [spire.transport :as transport]
            [spire.utils :as utils]
            [spire.state :as state]))

(defn ip-entry-process [[headline link & lines]]
  (let [[f b] (string/split headline #">")
        [num device flags] (string/split f #":\s+")
        device (keyword device)
        flags (-> flags
                  string/trim
                  (string/replace #"<" "")
                  (string/split #",")
                  (->> (map keyword)
                       (into #{})))
        opts (-> b
                 string/trim
                 (string/split #"\s+")
                 (->> (partition 2)
                      (map (fn [[k v]] [(keyword k)
                                        (try
                                          (Integer/parseInt v)
                                          (catch java.lang.NumberFormatException _
                                            (keyword v)))]))
                      (into {}))
                 )
        link (-> link
                 (string/split #"/")
                 second
                 (string/split #"\s+")
                 (->> (partition 2)
                      (map (fn [[k v]] [(keyword k)
                                        (try
                                          (Integer/parseInt v)
                                          (catch java.lang.NumberFormatException _
                                            v))]))
                      (into {}))
                 )
        addresses (-> lines
                      (->> (map string/trim)
                           (partition 2)
                           (map (fn [[overview flags]]
                                  (let [result (-> overview
                                                   (string/split #"\s+")
                                                   (->> (partition 2)
                                                        (map (fn [[k v]] [(keyword k)
                                                                          (try
                                                                            (Integer/parseInt v)
                                                                            (catch java.lang.NumberFormatException _
                                                                              v))]))
                                                        (into {})))
                                        flags (-> flags
                                                  string/trim
                                                  (string/split #"\s+")
                                                  (->> (partition 2)
                                                       (map (fn [[k v]] [(keyword k)
                                                                         (try
                                                                           (Integer/parseInt v)
                                                                           (catch java.lang.NumberFormatException _
                                                                             (keyword v)))
                                                                         ]))
                                                       (into {})))
                                        result (merge result flags)]
                                    result)))
                           (into [])))
        ]
    [(keyword device) {:flags flags
                       :options opts
                       :link link
                       :addresses addresses}]
    )
  )


(defn ipaddress-process
  "process 'ip address' output"
  [out]
  (-> out
      (string/split #"\n")
      (->> (partition-by #(re-find #"^\d+:" %))
           (partition 2)
           (map #(apply concat %))
           (map ip-entry-process)
           (into {})))
  )

(def bins
  [
   "bash" "ksh" "zsh" "csh" "tcsh"
   "dash" "sh" "stat" "ls" "id"
   "file" "touch" "chacl" "chown" "chgrp"
   "chmod" "cp" "cat" "echo" "printf"
   "date" "sed" "grep" "awk" "curl"
   "wget" "git" "tar" "rsync" "bzip2"
   "bzcat" "bunzip2" "gzip" "gunzip" "zip"
   "unzip" "uname" "lsb_release" "md5sum" "sha1sum"
   "sha256sum" "sha512sum" "apt" "dpkg" "yum"
   "rpm" "pkg"
   ])

(defn make-which []
  (apply str (map #(format "echo %s: `which %s`\n" % %) bins)))

(def char-choice "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")

(defn rand-char []
  (rand-nth char-choice))

(def sep-length 16)

(defn make-separator-slug []
  (apply str (map (fn [_] (rand-char)) (range sep-length)))
  )

(defn make-separator
  ([slug block-name]
   (str "---"
        slug
        ":"
        block-name
        "---")))

#_ (make-separator "paths")

(defn start-block [slug block-name]
  (format "echo \"%s\"\n" (make-separator slug block-name)))

(defn make-fact-script [slug]
  (str
   (start-block slug "paths")
   (make-which)
   (start-block slug "shell")
   (utils/embed-src "facts_shell.sh")
   (start-block slug "shell-id")
   (utils/embed-src "facts_id.sh")))

(defn extract-blocks [slug {:keys [out] :as res}]
  (let [lines (string/split-lines out)]
    (->> lines
         (partition-by #(string/starts-with? % (str "---" slug ":")))
         (partition 2)
         (map (fn [[header lines]]
                [(let [tag (-> header
                               first
                               (string/split #":")
                               second)
                       tag (subs tag 0 (- (count tag) 3))]
                   (keyword tag))
                 (vec lines)]))
         (into {}))))

(defn get-facts []
  (let [host-string state/*host-string*
        session state/*connection*
        slug (make-separator-slug)
        script (make-fact-script slug)]
    (->> (ssh/ssh-exec session script "" "UTF-8" {})
         (extract-blocks slug)
)))

#_
(transport/ssh "localhost"
         (get-facts))
