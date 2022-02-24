(ns spire.module.pkg
  (:require [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.utils :as utils]
            [clojure.string :as string]))

;; defaults
(def options-match-choices #{:first :last :all})
(def options-match-default :first)

(def failed-result {:exit 1 :out "" :err "" :result :failed})

(defmulti make-script (fn [command opts] command))

(defmulti preflight (fn [command opts] command))

(defmulti process-result (fn [command opts result] command))

;;
;; (pkg :update)
;;
(defmethod preflight :update [_ _]
  (facts/check-bins-present #{:pkg}))

(defmethod make-script :update [_ _]
  "pkg update")

(defmethod process-result :upgrade
  [_ _ {:keys [out err exit] :as result}]
  (if (zero? exit)
    (assoc result
           :result :ok
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )
    (assoc result
           :result :failed
           :out-lines (string/split out #"\n")
           :err-lines (string/split err #"\n")
           )))

;;
;; (pkg :install ...)
;;
(defmethod preflight :install [_ _]
  (when-not (facts/get-fact [:paths :pkg])
    {:exit 1
     :out ""
     :err "pkg module requires pkg installed and present in the path."
     :result :failed}))

(defmethod make-script :install [_ package-or-packages]
  (let [package-string (if (string? package-or-packages)
                         package-or-packages
                         (string/join " " package-or-packages))]
    (str "pkg install -y " package-string)))

(defmethod process-result :install
  [_ _ {:keys [out err exit] :as result}]
  (cond
    (and (zero? exit) (re-find #"INSTALLED" out))
    (assoc result
           :exit 0
           :result :changed)

    (and (zero? exit) (not (re-find #"INSTALLED" out)))
    (assoc result
           :exit 0
           :result :ok)

    (zero? exit)
    (assoc result
           :exit 0
           :result :ok)

    (= 255 exit)
    (assoc result
           :exit 0
           :result :changed)

    :else
    (assoc result
           :result :failed)))


;;
;; (pkg :remove ...)
;;
(defmethod preflight :remove [_ _]
  (when-not (facts/get-fact [:paths :pkg])
    {:exit 1
     :out ""
     :err "pkg module requires pkg installed and present in the path."
     :result :failed}))

(defmethod make-script :remove [_ package-or-packages]
  (let [package-string (if (string? package-or-packages)
                         package-or-packages
                         (string/join " " package-or-packages))]
    (str "pkg remove -y " package-string)))

(defmethod process-result :remove
  [_ _ {:keys [out err exit] :as result}]
  (cond
    (and (zero? exit) (re-find #"REMOVED" out))
    (assoc result
           :exit 0
           :result :changed)

    (and (zero? exit) (not (re-find #"REMOVED" out)))
    (assoc result
           :exit 0
           :result :ok)



    (= 65 exit)
    (assoc result
           :exit 0
           :result :ok)

    :else
    (assoc result
           :result :failed)))



(utils/defmodule pkg* [command opts]
  [host-string session {:keys [exec-fn sudo] :as shell-context}]
  (or
   (preflight command opts)
   (let [result (->>
                 (ssh/ssh-exec session (make-script command opts) "" "UTF-8" {:sudo sudo})
                 (process-result command opts))]
     (facts/update-facts-paths!)
     result)))

(defmacro pkg
  "manage packages on a FreeBSD system.
  (pkg command [package-or-packages])

  given:

  `command`: The overall command to execute. Should be `:install`,
  `:remove` or `:update`.

  `package-or-packages`: either a single package name as a string, or
  a sequence of package names. Not needed when specifying `:update`
  "
  [& args]
  `(utils/wrap-report ~&form (pkg* ~@args)))

(def documentation
  {
   :module "pkg"
   :blurb "Manage packages on a FreeBSD system."
   :description
   [
    "This module manages the installation and removal of FreeBSD packages."]
   :form "(pkg command & [package-or-packages])"
   :args
   [{:arg "command"
     :desc "The overall command to execute. Must be one of: `:install` or `:remove`."
     :values
     [[:install "Ensure the specied package is installed on the system"]
      [:remove "Ensure the specified package is removed from the syste,"]]}
    {:arg "package-or-packages"
     :desc "A package name or a list of packages"}]})
