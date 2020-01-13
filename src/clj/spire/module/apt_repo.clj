(ns spire.module.apt-repo
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]
            [clj-http.lite.client :as client]
            [clojure.data.json :as json]
            ))

;; defaults
(def options-match-choices #{:first :last :all})
(def options-match-default :first)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (line-in-file :present ...)
;;
(defmethod preflight :present [_ _]
  (when-not (facts/get-fact [:paths :apt-key])
    {:exit 1
     :out ""
     :err "apt-repo module requires apt-key installed and present in the path."
     :result :failed}))

(defmethod make-script :present [_ repo]
    (let [repo "ppa:wireguard/wireguard"
          ppa? (string/starts-with? repo "ppa:")]
      (if ppa?
        (let [[_ ppa] (string/split repo #":")
              [ppa-owner ppa-name] (string/split ppa #"/")
              ppa-name (or ppa-name "ppa")
              codename (name (facts/get-fact [:system :codename]))
              deb-line (format "deb http://ppa.launchpad.net/%s/%s/ubuntu %s main" ppa-owner ppa-name codename)
              debsrc-line (format "deb-src http://ppa.launchpad.net/%s/%s/ubuntu %s main" ppa-owner ppa-name codename)
              contents (str deb-line "\n" "# " debsrc-line)]
          (utils/make-script
           "apt_repo_present.sh"
           {:FILE (some->
                   "/etc/apt/sources.list.d/%s-%s-%s-%s.list"
                   (format ppa-owner "ubuntu" ppa-name codename)
                   utils/path-escape)
            :CONTENTS (some->> contents utils/path-escape)
            :PPA_NAME ppa-name
            :PPA_OWNER ppa-owner
            :CODENAME codename
            })))))

(defmethod process-result :present
  [_ _ {:keys [out err exit] :as result}]
  (cond
    (zero? exit)
    (assoc result
           :result :ok
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )

    (= 255 exit)
    (assoc result
           :result :changed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )

    :else
    (assoc result
           :result :failed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n"))))

(utils/defmodule apt-repo* [command opts]
  [host-config session]
  (or
   (preflight command opts)
   (->>
      (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {})
      (process-result command opts))))

(defmacro apt-repo [& args]
  `(utils/wrap-report ~*file* ~&form (apt-repo* ~@args)))
