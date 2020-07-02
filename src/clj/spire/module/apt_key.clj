(ns spire.module.apt-key
  (:require [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]
            ))

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (apt-key :list ...)
;;
(defmethod preflight :list [_ _]
  (facts/check-bins-present #{:sed :grep :awk :apt-key :curl :bash}))

(defmethod make-script :list [_ {:keys [repo filename]}]
  (utils/make-script
   "apt_key_list.sh"
   {}))

(defn process-key [out-lines]
  (loop [[line & remains] out-lines
         present-section nil
         data {}]
    (if line
      (if (re-find #"^\w" line)
        (let [[section body] (string/split line #"\s+" 2)]
          (recur remains (keyword section) (assoc data (keyword section) [body])))
        (recur remains present-section
               (update data present-section conj (string/trim line))))
      data)))

(defn process-apt-key-list-output [out-lines]
  (let [file-keys
        (loop [[line & remains] out-lines
               present-file nil
               lines {}]
          (if line
            (if (and remains (string/starts-with? (first remains) "-----"))
              (recur (rest remains) line lines)
              (recur remains present-file (update lines present-file #(conj (or %1 []) %2) line)))
            lines))]
    (->> (for [[k v] file-keys]
           [k
            (->> v
                 (partition-by empty?)
                 (filter #(not= % '("")))
                 (mapv process-key))])
         (into {}))))

(defmethod process-result :list
  [_ _ {:keys [out err exit] :as result}]
  (let [out-lines (string/split out #"\n")]
    (cond
      (zero? exit)
      (assoc result
             :result :ok
             :out-lines out-lines
             :keys (process-apt-key-list-output out-lines)
             )

      (= 255 exit)
      (assoc result
             :result :changed
             :out-lines out-lines
             )

      :else
      (assoc result
             :result :failed
             :out-lines out-lines))))


;;
;; (apt-key :present ...)
;;
(defmethod preflight :present [_ _]
  (facts/check-bins-present #{:sed :grep :awk :apt-key :curl :bash}))

(defmethod make-script :present [_ {:keys [fingerprint public-key public-key-url keyring]}]
  (utils/make-script
   "apt_key_present.sh"
   {:FINGERPRINT (some-> fingerprint
                         name
                         (string/replace #"\s+" "")
                         (string/upper-case))
    :PUBLIC_KEY public-key
    :PUBLIC_KEY_URL public-key-url
    :KEYRING keyring}))

(defmethod process-result :present
  [_ _ {:keys [out err exit] :as result}]
  (let [out-lines (string/split out #"\n")]
    (cond
      (zero? exit)
      (assoc result
             :result :ok
             :out-lines out-lines
             )

      (= 255 exit)
      (assoc result
             :result :changed
             :out-lines out-lines
             )

      :else
      (assoc result
             :result :failed
             :out-lines out-lines))))




(utils/defmodule apt-key* [command opts]
  [host-config session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (or
   (preflight command opts)
   (->>
    (exec-fn session (shell-fn "bash") (stdin-fn (make-script command opts)) "UTF-8" {})
    (process-result command opts))))

(defmacro apt-key
  "manage the presence or absence of extra apt repositories.
  (apt-repo command opts)

  given:

  `command`: Should be one of `:present` or `:absent`

  `opts`: a hashmap of options with the following keys:

  `:repo` The repository line as it appears in apt sources file, or a
  ppa description line.

  `:filename` an optional filename base for the storage of the repo
  definition inside /etc/apt/sources.d
  "
  [& args]
  `(utils/wrap-report ~&form (apt-key* ~@args)))

(def documentation
  {
   :module "apt-repo"
   :blurb "Manage extra apt repositories"
   :description
   [
    "This module manages the presence of extra apt repositories."]
   :form "(apt-repo command opts)"
   :args
   [{:arg "command"
     :desc "The overall command to execute. Should be one of `:present` or `:absent`"
     :values
     [[:present "Ensure the specified apt repository is present on the machine"]
      [:absent "Ensure the specified apt repository is absent on the machine"]]}
    {:arg "options"
     :desc "A hashmap of options"}]

   :opts
   [
    [:repo
     {:description ["The repository line as it appears in an apt source file."
                    "A ppa description string."]
      :type :string
      :required true}]
    [:filename
     {:description ["The base filename to use when storing the config."
                    "Only necessary when `command` is `:present`."
                    "When `command` is `:absent` the repo lists are searched and all references to the repo are removed."]}]
    ]

   :examples
   [
    {:description
     "Add specified repository into sources list using specified filename."
     :form "
(apt-repo :present {:repo \"deb http://dl.google.com/linux/chrome/deb/ stable main\"
                    :filename \"google-chrome\"})"}
    {:description
     "Install an ubuntu ppa apt source for php packages"
     :form "
(apt-repo :present {:repo \"ppa:ondrej/php\"})"}

    {:description
     "Remove the ubuntu php ppa"
     :form "
(apt-repo :absent {:repo \"ppa:ondrej/php\"})"}

    ]
   })



(def out-lines
  ["/etc/apt/trusted.gpg"
             "--------------------"
             "pub   rsa4096 2016-04-12 [SC]"
             "      EB4C 1BFD 4F04 2F6D DDCC  EC91 7721 F63B D38B 4796"
             "uid           [ unknown] Google Inc. (Linux Packages Signing Authority) <linux-packages-keymaster@google.com>"
             "sub   rsa4096 2019-07-22 [S] [expires: 2022-07-21]"
             ""
             "pub   rsa4096 2014-06-13 [SC]"
             "      9FD3 B784 BC1C 6FC3 1A8A  0A1C 1655 A0AB 6857 6280"
             "uid           [ unknown] NodeSource <gpg@nodesource.com>"
             "sub   rsa4096 2014-06-13 [E]"
             ""
             "pub   rsa4096 2017-04-05 [SC]"
             "      DBA3 6B51 81D0 C816 F630  E889 D980 A174 57F6 FB06"
             "uid           [ unknown] Open Whisper Systems <support@whispersystems.org>"
             "sub   rsa4096 2017-04-05 [E]"
             ""
             "pub   rsa4096 2017-04-11 [SC] [expired: 2019-09-28]"
             "      D4CC 8597 4C31 396B 18B3  6837 D615 560B A5C7 FF72"
             "uid           [ expired] Opera Software Archive Automatic Signing Key 2017 <packager@opera.com>"
             ""
             "/etc/apt/trusted.gpg.d/deadsnakes_ubuntu_ppa.gpg"
             "------------------------------------------------"
             "pub   rsa4096 2017-07-29 [SC]"
             "      F23C 5A6C F475 9775 95C8  9F51 BA69 3236 6A75 5776"
             "uid           [ unknown] Launchpad PPA for deadsnakes"
             ""
             "/etc/apt/trusted.gpg.d/kritalime_ubuntu_ppa.gpg"
             "-----------------------------------------------"
             "pub   rsa4096 2015-04-24 [SC]"
             "      7099 01D8 205B F7EC 0606  2E0F 78F9 8870 01CE E17F"
             "uid           [ unknown] Launchpad PPA for Krita Lime (*experimental*)"
             ""
             "/etc/apt/trusted.gpg.d/mfikes_ubuntu_planck.gpg"
             "-----------------------------------------------"
             "pub   rsa4096 2017-02-10 [SC]"
             "      A5D6 8129 87A6 E535 79AF  0308 D3D7 4311 1F32 7606"
             "uid           [ unknown] Launchpad PPA for Mike Fikes"
             ""
             "/etc/apt/trusted.gpg.d/obsproject_ubuntu_obs-studio.gpg"
             "-------------------------------------------------------"
             "pub   rsa4096 2014-10-19 [SC]"
             "      BC73 45F5 2207 9769 F5BB  E987 EFC7 1127 F425 E228"
             "uid           [ unknown] Launchpad PPA for obsproject"
             ""
             "/etc/apt/trusted.gpg.d/ondrej_ubuntu_php.gpg"
             "--------------------------------------------"
             "pub   rsa1024 2009-01-26 [SC]"
             "      14AA 40EC 0831 7567 56D7  F66C 4F4E A0AA E526 7A6C"
             "uid           [ unknown] Launchpad PPA for Ondřej Surý"
             ""
             "/etc/apt/trusted.gpg.d/openjdk-r_ubuntu_ppa.gpg"
             "-----------------------------------------------"
             "pub   rsa1024 2010-04-12 [SC]"
             "      DA1A 4A13 543B 4668 53BA  F164 EB9B 1D88 86F4 4E2A"
             "uid           [ unknown] Launchpad OpenJDK builds (all archs)"
             ""
             "/etc/apt/trusted.gpg.d/peek-developers_ubuntu_stable.gpg"
             "--------------------------------------------------------"
             "pub   rsa4096 2017-02-14 [SC]"
             "      8C95 3129 9E7D F2DC F681  B499 9578 5391 76BA FBC6"
             "uid           [ unknown] Launchpad PPA for Peek Developers"
             ""
             "/etc/apt/trusted.gpg.d/steam.gpg"
             "--------------------------------"
             "pub   rsa2048 2012-12-07 [SC]"
             "      BA18 16EF 8E75 005F CF5E  27A1 F24A EA9F B054 98B7"
             "uid           [ unknown] Valve Corporation <linux@steampowered.com>"
             "sub   rsa2048 2012-12-07 [E]"
             ""
             "/etc/apt/trusted.gpg.d/ubuntu-audio-dev_ubuntu_alsa-daily.gpg"
             "-------------------------------------------------------------"
             "pub   rsa1024 2009-07-01 [SC]"
             "      4E9F 485B F943 EF0E ABA1  0B5B D225 991A 72B1 94E5"
             "uid           [ unknown] Launchpad Ubuntu Audio Dev team PPA"
             ""
             "/etc/apt/trusted.gpg.d/ubuntu-keyring-2012-archive.gpg"
             "------------------------------------------------------"
             "pub   rsa4096 2012-05-11 [SC]"
             "      790B C727 7767 219C 42C8  6F93 3B4F E6AC C0B2 1F32"
             "uid           [ unknown] Ubuntu Archive Automatic Signing Key (2012) <ftpmaster@ubuntu.com>"
             ""
             "/etc/apt/trusted.gpg.d/ubuntu-keyring-2012-cdimage.gpg"
             "------------------------------------------------------"
             "pub   rsa4096 2012-05-11 [SC]"
             "      8439 38DF 228D 22F7 B374  2BC0 D94A A3F0 EFE2 1092"
             "uid           [ unknown] Ubuntu CD Image Automatic Signing Key (2012) <cdimage@ubuntu.com>"
             ""
             "/etc/apt/trusted.gpg.d/ubuntu-keyring-2018-archive.gpg"
             "------------------------------------------------------"
             "pub   rsa4096 2018-09-17 [SC]"
             "      F6EC B376 2474 EDA9 D21B  7022 8719 20D1 991B C93C"
             "uid           [ unknown] Ubuntu Archive Automatic Signing Key (2018) <ftpmaster@ubuntu.com>"
             ""
             "/etc/apt/trusted.gpg.d/webupd8team_ubuntu_java.gpg"
             "--------------------------------------------------"
             "pub   rsa1024 2010-05-04 [SC]"
             "      7B2C 3B08 89BF 5709 A105  D03A C251 8248 EEA1 4886"
             "uid           [ unknown] Launchpad VLC"
             ""
             "/etc/apt/trusted.gpg.d/wireguard_ubuntu_wireguard.gpg"
             "-----------------------------------------------------"
             "pub   rsa4096 2016-11-16 [SC]"
             "      E1B3 9B6E F6DD B965 6479  7591 AE33 835F 504A 1A25"
             "uid           [ unknown] Launchpad PPA for wireguard-ppa"]
  )
