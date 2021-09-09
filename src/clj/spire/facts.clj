(ns spire.facts
  (:require [clojure.string :as string]
            [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.state :as state]))

(defonce state (atom {}))

(def bins
  [
   "bash" "ksh" "zsh" "csh" "tcsh" "fish"
   "dash" "sh" "sash" "yash" "zsh"
   "stat" "ls" "id" "sudo" "lsattr"
   "file" "touch" "chacl" "chown" "chgrp" "chattr"
   "chmod" "cp" "cat" "cut" "printf" "find"
   "head" "tail" "sysctl" "true" "false"
   "date" "sed" "grep" "awk" "curl" "mkdir"
   "groupadd" "groupmod" "groupdel"
   "useradd" "usermod" "userdel"
   "wget" "git" "tar" "rsync" "bzip2" "aws"
   "bzcat" "bunzip2" "gzip" "gunzip" "zip"
   "unzip" "uname" "lsb_release"
   "md5sum" "md5"
   "sha1sum" "sha1"
   "sha224sum" "sha224"
   "sha256sum" "sha256"
   "sha384sum" "sha384"
   "sha512sum" "sha512"
   "scp" "service"
   "apt" "apt-get" "dpkg" "yum" "rpm" "pkg" "apt-key"
   "systemctl" "journalctl"
   ])

(defn make-which [shell]
  (case shell
    :csh
    ;; `where` on freebsd. `which` on linux. wtf?
    (str (apply str (map #(format "echo %s: `which %s`\n" % %) bins))
         "\n"
         "exit 0")

    :fish
    (apply str (map #(format "echo %s: (which %s)\n" % %) bins))

    (apply str (map #(format "echo %s: `which %s`\n" % %) bins))
    ))

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
     :string uname}))

(defn process-shell-uname [[command file uname platform node-name os kernel-release kernel-version]]
  {:string uname
   :platform platform
   :node node-name
   :os os
   :kernel {:release kernel-release
            :version kernel-version}})

(defn process-shell-info [[command path uname platform node-name os kernel-release kernel-version]]
  {:command command
   :path path})

(defn process-system [uname-data shell-data]
  (let [detect (:detect shell-data)
        sh (case (:command shell-data)
             "bash" :bash
             "dash" :dash
             "yash" :yash
             "tcsh" :tcsh
             "fish" :fish
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
  (let [res (some->> lsb-out
                     :out
                     string/split-lines
                     (map #(string/split % #":\t"))
                     (into {}))]
    {:codename (-> "Codename" res string/lower-case keyword)
     :distro (-> "Distributor ID" res string/lower-case keyword)
     :release (res "Release")
     :description (res "Description")}))

(def mac-codenames
  {
   "10.0" :cheetah
   "10.1" :cheetah
   "10.2" :cheetah
   "10.3" :panther
   "10.4" :tiger
   "10.5" :leopard
   "10.6" :snow-leopard
   "10.7" :lion
   "10.8" :mountain-lion
   "10.9" :mavericks
   "10.10" :yosemite
   "10.11" :el-capitan
   "10.12" :sierra
   "10.13" :high-sierra
   "10.14" :mojave
   "10.15" :catalina
   })

(defn guess-mac-codename [version]
  (let [[_ maj-min _] (re-matches #"(\d+\.\d+).*" version)]
    (get mac-codenames maj-min)))

(defn process-system-profiler [sp-out]
  (let [res (some->> sp-out
                     :out
                     string/split-lines
                     (map string/trim)
                     (map #(string/split % #":\s+" 2))
                     (filter #(= 2 (count %)))
                     (into {}))
        system-version (res "System Version")
        [_ distro version build] (re-matches #"([\w\s]+)\s+([\d.]+)\s+\((\S+)\)" system-version)
        ]
    {:description system-version
     :codename (guess-mac-codename version)
     :distro :macos
     :release version}
    )
  )

(defn runner [script shell]
  (let [session (state/get-connection)
        {:keys [exec-fn exec]} (state/get-shell-context)]
    (if (= :local exec)
      (exec-fn nil (name shell) script "UTF-8" {})
      (exec-fn session script "" "UTF-8" {}))))

(defn fetch-shell []
  (let [{:keys [exit out err] :as result} (runner "echo $SHELL" "sh")
        shell-path (string/trim out)]
    (assert (zero? exit) (format "Initial shell check `echo $SHELL` failed: %s" err))
    (let [shell (last (string/split shell-path #"/" -1))]
      (if (and shell (not (empty? shell)))
        (keyword shell)
        :sh))))

(defmulti fetch-shell-facts identity)

(defn fetch-facts []
  (fetch-shell-facts (fetch-shell)))

(defn run-and-return-lines [shell script assert-format]
  (let [{:keys [out err exit]} (runner script shell)]
    (assert (zero? exit) (format assert-format exit err))
    (string/split-lines out)))

(defn run-lsb-release [shell]
  (some->
   (runner "lsb_release -a" shell)
   process-lsb-release))

(defn run-system-profiler [shell]
  (some->
   (runner "system_profiler SPSoftwareDataType" shell)
   process-system-profiler))

(defn process-release-info [{:keys [os]} shell]
  (cond
    (= :linux os) (run-lsb-release shell)
    (= :darwin os) (run-system-profiler shell)
    ;; what about freebsd?
    :else nil))

(defn process-id-name-substring [substring]
  (let [[_ id name] (re-matches #"(\d+)\(([\d\w_\.\-]+)\)" substring)]
    {:id (Integer/parseInt id)
     :name name}))

(defn process-id [id-out]
  (let [{:keys [gid uid groups]}
        (-> id-out first string/trim (string/split #"\s+")
            (->> (take 3)
                 (map (fn [line]
                        (let [[type val] (string/split line #"=" 2)
                              vals (->> (string/split val #",")
                                        (mapv process-id-name-substring))]
                          [(keyword type) vals])))
                 (into {})))]
    {:gid (first gid)
     :uid (first uid)
     :groups groups
     :group-ids (into #{} (map :id groups))
     :group-names (into #{} (map :name groups))
     }))

(defn get-facts-fish-script []
  (utils/embed-src "facts_fish.fish"))

(defn fetch-shell-facts-fish [shell]
  (let [base-shell-uname-output (run-and-return-lines
                                 shell
                                 (get-facts-fish-script)
                                 "facts_fish.fish script exited %d: %s")
        shell-version-output (run-and-return-lines shell
                                                   "echo $FISH_VERSION"
                                                   "retrieving fish version script exited %d: %s")
        paths-output (run-and-return-lines shell (make-which shell)
                                           "retrieving paths script exited %d: %s")
        id-out (run-and-return-lines shell "id" "running remote `id` command exited %d: %s")

        uname-data (process-shell-uname base-shell-uname-output)
        shell-data (process-shell-info base-shell-uname-output)
        version (first shell-version-output)
        detect (str "fish " version)
        paths (process-paths {:paths paths-output})
        shell-data (assoc shell-data :detect detect :version version)
        system-data (process-system uname-data shell-data)
        release-info (process-release-info system-data shell)
        system-data (into system-data release-info)]
    (->> {:shell shell-data
          :uname uname-data
          :system system-data
          :user (process-id id-out)
          :paths paths
          :ssh-config (state/get-host-config)})))

(defmethod fetch-shell-facts :fish [shell]
  (fetch-shell-facts-fish shell))

(defn get-facts-csh-script []
  (utils/embed-src "facts_shell.csh"))

(defn get-facts-id-script []
  (utils/embed-src "facts_id.sh"))

(defn fetch-shell-facts-csh [shell]
  (let [base-shell-uname-output (run-and-return-lines
                                 shell
                                 (get-facts-csh-script)
                                 "facts_shell.csh script exited %d: %s")
        shell-version-output (run-and-return-lines shell
                                                   (get-facts-id-script)
                                                   "facts_id.sh exited %d: %s")
        paths-output (run-and-return-lines shell (make-which shell)
                                           "retrieving paths script exited %d: %s")
        id-out (run-and-return-lines shell "id" "running remote `id` command exited %d: %s")

        uname-data (process-shell-uname base-shell-uname-output)
        shell-data (process-shell-info base-shell-uname-output)
        detect (first shell-version-output)
        version (last (string/split detect #"\s+"))
        paths (process-paths {:paths paths-output})
        shell-data (assoc shell-data :detect detect :version version)
        system-data (process-system uname-data shell-data)
        release-info (process-release-info system-data shell)
        system-data (into system-data release-info)]
    (->> {:shell shell-data
          :uname uname-data
          :system system-data
          :user (process-id id-out)
          :paths paths
          :ssh-config (state/get-host-config)})))

(defmethod fetch-shell-facts :csh [shell]
  (fetch-shell-facts-csh shell))

(defn get-facts-sh-script []
  (utils/embed-src "facts_shell.sh"))

(defn fetch-shell-facts-default [shell]
  (let [base-shell-uname-output (run-and-return-lines
                                 shell
                                 (get-facts-sh-script)
                                 "facts_shell.sh script exited %d: %s")
        shell-version-output (run-and-return-lines shell
                                                   (get-facts-id-script)
                                                   "facts_id.sh exited %d: %s")
        paths-output (run-and-return-lines shell (make-which shell)
                                           "retrieving paths script exited %d: %s")
        id-out (run-and-return-lines shell "id" "running remote `id` command exited %d: %s")

        uname-data (process-shell-uname base-shell-uname-output)
        shell-data (process-shell-info base-shell-uname-output)
        detect (first shell-version-output)
        version (last (string/split detect #"\s+"))
        paths (process-paths {:paths paths-output})
        shell-data (assoc shell-data :detect detect :version version)
        system-data (process-system uname-data shell-data)
        release-info (process-release-info system-data shell)
        system-data (into system-data release-info)]
    (->> {:shell shell-data
          :uname uname-data
          :system system-data
          :user (process-id id-out)
          :paths paths
          :ssh-config (state/get-host-config)})))

(defmethod fetch-shell-facts :default [shell]
  (fetch-shell-facts-default shell))

(defn update-facts! []
  (let [facts (fetch-facts)]
    (swap! state update (:host-string (state/get-host-config)) merge facts)))

(defn get-fact [& [path default]]
  (let [host-string (:host-string (state/get-host-config))]
    (if (@state host-string)
      (get-in @state (concat [host-string] path default))
      (get-in (update-facts!) (concat [host-string] path default))))
  )

(defn fetch-facts-paths
  ([shell]
   (process-paths
    {:paths (run-and-return-lines shell (make-which shell)
                                  "retrieving paths script exited %d: %s")}))
  ([]
   (fetch-facts-paths (get-fact [:system :shell]))))

(defn update-facts-paths! []
  (let [paths (fetch-facts-paths)]
    (swap! state assoc-in [(:host-string (state/get-host-config)) :paths] paths)))

(defn update-facts-user! [id-out]
  (swap! state assoc-in [(:host-string (state/get-host-config)) :user] (process-id id-out)))

(defn replace-facts-user! [user-facts]
  (swap! state assoc-in [(:host-string (state/get-host-config)) :user] user-facts))

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

(defn check-bins-present
  "Ensure all the binaries specified are present.
  Binaries are specified as keywords. They are looked up in facts under :paths
  "
  [bins]
  (let [paths (get-fact [:paths])
        not-present
        (->> bins
             (map #(when (not (paths %)) (name %)))
             (filter identity)
             )]
    (when (seq not-present)
      {:exit 1
       :out ""
       :err (format "missing commands: %s" (string/join ", " not-present))
       :result :failed})))
