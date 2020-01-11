(ns spire.facts
  (:require [clojure.string :as string]
            [spire.ssh :as ssh]
            [spire.transport :as transport]
            [spire.utils :as utils]
            [spire.state :as state]))

(defonce state (atom {}))

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
   "chmod" "cp" "cat" "printf" "find"
   "date" "sed" "grep" "awk" "curl"
   "wget" "git" "tar" "rsync" "bzip2"
   "bzcat" "bunzip2" "gzip" "gunzip" "zip"
   "unzip" "uname" "lsb_release"
   "md5sum" "md5"
   "sha1sum" "sha1"
   "sha224sum" "sha224"
   "sha256sum" "sha256"
   "sha384sum" "sha384"
   "sha512sum" "sha512"
   "apt" "apt-get" "dpkg" "yum" "rpm" "pkg"
   "apt-key"
   ])

(defn make-which [shell]
  (case shell
    :csh
    (apply str (map #(format "echo %s: `where %s`\n" % %) bins))

    (apply str (map #(format "echo %s: `which %s`\n" % %) bins))
    ))

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
   #_ (start-block slug "paths")
   #_ (make-which)
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

(defn process-uname [uname]
  (let [[kernel-name node-name kernel-release kernel-version machine processor platform os]
        (string/split uname #"\s+")]
    {:kernel {:name kernel-name
              :release kernel-release
              :version kernel-version}
     :machine machine
     :processor processor
     :platform platform
     :os os
     :node node-name
     :string uname
     }
    )
  )

(defn process-shell-uname [[command file uname platform node-name os kernel-release kernel-version] shell-id]
  {:string uname
   :platform platform
   :node node-name
   :os os
   :kernel {:release kernel-release
            :version kernel-version}})

(defn process-shell-info [[command file uname platform node-name os kernel-release kernel-version] shell-id]
  (let [[path info] (string/split file #":\s*" 2)]
    {:command command
     :path path
     :info info
     :detect (first shell-id)}))

(defn process-system [uname-data shell-data]
  (let [detect (:detect shell-data)
        sh (case (:command shell-data)
            "bash" :bash
            "zsh"  :zsh
            "csh"  :csh
            "ksh"  :ksh
            "sh"   (if (string/starts-with? detect "ash")
                     :ash
                     :sh))]
    {:os (-> uname-data :os string/lower-case keyword)
     :platform (-> uname-data :platform keyword)
     :shell sh
     }))

(defn process-host-string [host-config facts]
  (assoc facts :ssh-config host-config))

(defn process-facts [{:keys [paths shell shell-id] :as data}]
  (let [uname-data (process-shell-uname shell shell-id)
        shell-data (process-shell-info shell shell-id)]
    ;; initial facts
    {
     :system (process-system uname-data shell-data)
     :uname uname-data
     :shell shell-data
     :raw-data data
     }))

(defn process-paths [{:keys [paths]}]
  (let [
        path-data
        (for [line paths]
          (let [[k v] (string/split line #":\s*")
                [p _] (some-> v (string/split #"\s+"))
                ]
            (when p [(keyword k) p])))
        new-paths (->> path-data
                       (filter identity)
                       (into {}))]
    new-paths))

(defn process-lsb-release [lsb-out]
  (prn "lsb:" (->> lsb-out
                 :out
                 string/split-lines))
  (let [res (->> lsb-out
                 :out
                 string/split-lines
                 (map #(string/split % #":\t"))
                 (into {}))]
    {:codename (-> "Codename" res string/lower-case keyword)
     :distro (-> "Distributor ID" res string/lower-case keyword)
     :release (res "Release")
     :description (res "Description")}))

(defn fetch-facts []
  (let [host-string state/*host-string*
        host-config state/*host-config*
        session state/*connection*
        slug (make-separator-slug)
        script (make-fact-script slug)]
    (comment (println "fetch-facts:" script)
             (println "session:" session))
    (let [facts (->> (ssh/ssh-exec session script "" "UTF-8" {})
                     (extract-blocks slug)
                     process-facts
                     (process-host-string host-config))
          shell (get-in facts [:system :shell])
          path-script (str
                         (start-block slug "paths")
                         (make-which shell))
          path-results (->> (ssh/ssh-exec session path-script "" "UTF-8" {})
                            (extract-blocks slug)
                            process-paths)
          _ (prn "facts pre:" facts)
          extra-system (ssh/ssh-exec
                        session
                        "lsb_release -a"
                        #_(cond
                          (= :linux (get-in facts [:system :os]))
                          "lsb_release -a"

                          :else
                          "echo unknown")
                        "" "UTF-8" {})
          release-info (process-lsb-release extra-system)
          ]
      (-> facts
          (assoc :paths path-results)
          (update :system into release-info)))))

(defn update-facts! []
  (let [facts (fetch-facts)]
    (swap! state update state/*host-string* merge facts)))

(defn get-fact [& [path default]]
  (if (@state state/*host-string*)
    (get-in @state (concat [state/*host-string*] path default))
    (get-in (update-facts!) (concat [state/*host-string*] path default)))
  )


#_
(transport/ssh "localhost"
         (get-facts))

(defn os []
  (get-fact [:system :os]))

(defn md5 []
  (or
   (get-fact [:paths :md5sum])
   (get-fact [:paths :md5])))


(defmacro on-os [ & pairs]
  (let [os (gensym)]
    `(let [~os (get-fact [:system :os])]
       (cond
         ~@(apply concat
                  (for [[pred form] (partition 2 pairs)]
                    [
                     (cond
                       (and (keyword? pred) (= pred :else))
                       `:else

                       (keyword? pred)
                       `(= ~pred ~os)

                       :else
                       `(~pred ~os))

                     form]))))))

(defmacro on-shell [ & pairs]
  (let [shell (gensym)]
    `(let [~shell (get-fact [:system :shell])]
       (cond
         ~@(apply concat
                  (for [[pred form] (partition 2 pairs)]
                    [
                     (cond
                       (and (keyword? pred) (= pred :else))
                       `:else

                       (keyword? pred)
                       `(= ~pred ~shell)

                       :else
                       `(~pred ~shell))

                     form]))))))

(defmacro on-distro [ & pairs]
  (let [shell (gensym)]
    `(let [~shell (get-fact [:system :distro])]
       (cond
         ~@(apply concat
                  (for [[pred form] (partition 2 pairs)]
                    [
                     (cond
                       (and (keyword? pred) (= pred :else))
                       `:else

                       (keyword? pred)
                       `(= ~pred ~shell)

                       :else
                       `(~pred ~shell))

                     form]))))))
