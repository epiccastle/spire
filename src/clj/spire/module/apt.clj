(ns spire.module.apt
  (:require [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]))

;; defaults
(def options-match-choices #{:first :last :all})
(def options-match-default :first)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

(defn make-env-command
  "Build an apt-get command string that sets DEBIAN_FRONTEND=noninteractive
  in a manner appropriate to the shell detected by eyre.

  The dispatch is based on the eyre shell `:type`. The csh/tcsh and
  sh-family variants invoke the running shell using the `:shell` path
  returned by eyre (rather than hardcoding `csh`/`tcsh`/`bash`), while
  the fish and fallback cases use an env-prefix or inline assignment
  form respectively."
  [apt-command]
  (let [shell-type (facts/get-fact [:shell :type])
        shell-path (facts/get-fact [:shell :shell])]
    (case shell-type
      :fish (str "env DEBIAN_FRONTEND=noninteractive " apt-command)
      (:csh :tcsh) (str shell-path " -c 'setenv DEBIAN_FRONTEND noninteractive; " apt-command "'")
      (:sh :bash :dash :zsh :ksh :busybox) (str shell-path " -c 'DEBIAN_FRONTEND=noninteractive " apt-command "'")
      (str "DEBIAN_FRONTEND=noninteractive " apt-command))))

;;
;; (apt :install ...)
;;
(defmethod preflight :install [_ _]
  (facts/check-bins-present #{:apt-get}))

(defmethod make-script :install [_ package-or-packages]
  (let [package-string (if (string? package-or-packages)
                         package-or-packages
                         (string/join " " package-or-packages))]
    (make-env-command (str "apt-get install -y " package-string))))

(defmethod process-result :install
  [_ _ {:keys [out err exit] :as result}]
  (if (zero? exit)
    (let [[_ upgraded installed removed]
          (re-find #"(\d+)\s*\w*\s*upgrade\w*, (\d+)\s*\w*\s*newly instal\w*, (\d+) to remove" out)

          upgraded (Integer/parseInt upgraded)
          installed (Integer/parseInt installed)
          removed (Integer/parseInt removed)
          ]
      (assoc result
             :result (if (and (zero? upgraded)
                              (zero? installed)
                              (zero? removed))
                       :ok
                       :changed)
             :out-lines (string/split out #"\n")
             :packages {:upgraded upgraded
                        :installed installed
                        :removed removed}))
    (assoc result :result :failed)))


;;
;; (apt :update)
;;
(defmethod preflight :update [_ _]
  (facts/check-bins-present #{:apt-get}))

(defmethod make-script :update [_ _]
  (make-env-command "apt-get update -y"))

(defn process-values [result func]
  (->> result
       (map (fn [[k v]] [k (func v)]))
       (into {})))

(defn process-apt-update-line [line]
  (let [[method remain] (string/split line #":" 2)
        method (-> method string/lower-case keyword)
        [url dist component size] (-> remain (string/split #"\s+" 5) rest (->> (into [])))
        size (some->> size (re-find #"\[(.+)\]") second)
        result {:method method
                :url url
                :dist dist
                :component component}]
    (if size
      (assoc result :size size)
      result)))

(defmethod process-result :update
  [_ _ {:keys [out err exit] :as result}]
  (if (zero? exit)
    (let [data (-> out
                   (string/split #"\n")
                   (->>
                    (filter #(re-find #"^\w+:\d+\s+" %))
                    (mapv process-apt-update-line))
                   )
          changed? (some #(= % :get) (map :method data))]
      (assoc result
             :result (if changed? :changed :ok)
             :out-lines (string/split out #"\n")
             :err-lines (string/split err #"\n")
             :update data))
    (assoc result
           :result :failed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )))


;;
;; (apt :remove ...)
;;
(defmethod preflight :remove [_ _]
  (facts/check-bins-present #{:apt-get}))

(defmethod make-script :remove [_ package-or-packages]
  (let [package-string (if (string? package-or-packages)
                         package-or-packages
                         (string/join " " package-or-packages))]
    (make-env-command (str "apt-get remove -y " package-string))))

(defmethod process-result :remove
  [_ _ {:keys [out err exit] :as result}]
  (if (zero? exit)
    (let [[_ upgraded installed removed]
          (re-find #"(\d+)\s*\w*\s*upgrade\w*, (\d+)\s*\w*\s*newly instal\w*, (\d+) to remove" out)

          upgraded (Integer/parseInt upgraded)
          installed (Integer/parseInt installed)
          removed (Integer/parseInt removed)
          ]
      (assoc result
             :result (if (and (zero? upgraded)
                              (zero? installed)
                              (zero? removed))
                       :ok
                       :changed)
             :out-lines (string/split out #"\n")
             :packages {:upgraded upgraded
                        :installed installed
                        :removed removed}))
    (assoc result :result :failed)))


;;
;; (apt :upgrade)
;;
(defmethod preflight :upgrade [_ _]
  (facts/check-bins-present #{:apt-get}))

(defmethod make-script :upgrade [_ _]
  (make-env-command "apt-get upgrade -y"))

(defmethod process-result :upgrade
  [_ _ {:keys [out err exit] :as result}]
  (if (zero? exit)
    (let [changed? true]
      (assoc result
             :result (if changed? :changed :ok)
             :out-lines (string/split out #"\n")
             :err-lines (string/split err #"\n")
             ;;:upgrade data
             ))
    (assoc result
           :result :failed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )))

(utils/defmodule apt* [command & [opts]]
  [host-config session {:keys [exec-fn sudo] :as shell-context}]
  (or
   (preflight command opts)
   (let [result (->>
                 (exec-fn session (make-script command opts) "" "UTF-8" {:sudo sudo})
                 (process-result command opts))]
     (facts/update-facts-paths!)
     result)))

(defmacro apt
  "manage packages through the apt package system.
  (apt command & [package-or-packages])

  given:

  `command`: The command to execute. Should be `:update`, `:upgrade`,
  `:install` or `:uninstall`

  `package-or-packages`: The name of a single package, or a sequence
  of package names.
  "
  [& args]
  `(utils/wrap-report ~&form (apt* ~@args)))

(def documentation
  {
   :module "apt"
   :blurb "Manage packages through the apt package system"
   :description
   [
    "This module manages the apt package system."
    "It can update the available package list."
    "It can upgrade the entire package set"
    "It can install and uninstall packages"]
   :form "(apt command options)"
   :args
   [{:arg "command"
     :desc "The overall command to execute. Should be one of `:update`, `:upgrade`, `:install` and `:uninstall`"
     :values
     [[:update "Download all the latest package indexes to the machine."]
      [:upgrade "Upgrade any out of date packages to the latest versions."]
      [:install "Install one or more packages."]
      [:uninstall "Uninstall one or more packages."]]}
    {:arg "options"
     :desc "In `:install` and `:uninstall` this can be a single package name (specified as a string), or many package names (a sequence or vector of many strings). In `:update` and `:upgrade` commands this is ignored."}]

   :examples
   [
    {:description "Update the package list"
     :form "
(apt :update)"}
    {:description "Upgrade the installed packages to the latest versions"
     :form "
(apt :upgrade)"}
    {:description "Install the traceroute package"
     :form "
(apt :install \"traceroute\")"}
    {:description "Install some network tools"
     :form "
(apt :install [\"traceroute\" \"netperf\" \"iptraf\" \"nmap\"])"}
    {:description "Uninstall some network tools"
     :form "
(apt :remove [\"traceroute\" \"netperf\" \"iptraf\" \"nmap\"])"}]
   })
