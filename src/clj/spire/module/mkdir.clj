(ns spire.module.mkdir
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [spire.facts :as facts]
            [clojure.string :as string]))

(defn preflight [{:keys [path owner group mode] :as opts}]
  (facts/check-bins-present [:bash :mkdir :chown :chgrp :chmod]))

(defn make-script [{:keys [path owner group mode] :as opts}]
  (utils/make-script
   "mkdir.sh"
   {:FILE (some->> path utils/path-escape)
    :OWNER owner
    :GROUP group
    :MODE (if (number? mode) (format "%o" mode)  mode)
    :STAT (facts/on-os :linux "%u %g %a" :else "%u %g %p")
    }))

(defn process-result [{:keys [path owner group mode] :as opts} {:keys [out err exit] :as result}]
  (let [result (assoc result
                      :out-lines (string/split-lines out)
                      :err-lines (string/split-lines err))]
    (cond
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
             :result :failed))))

(utils/defmodule mkdir* [{:keys [path owner group mode] :as opts}]
  [host-string session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (or
   (preflight opts)
   (let [result
         (->>
          (exec-fn session (shell-fn "bash") (stdin-fn (make-script opts)) "UTF-8" {})
          (process-result opts))]
     result))
  )

(defmacro mkdir [& args]
  `(utils/wrap-report ~&form (mkdir* ~@args)))

(def documentation
  {
   :module "mkdir"
   :blurb "Ensure a directory is present"
   :description
   [
    "This module ensures a directory is present at the specified path and that it has the specified ownership and modification flags. If parent directories are not present, they are also created."]
   :form "(mkdir options)"
   :args
   [{:arg "options"
     :desc "A hashmap of options"}]
   :opts
   [[:path
     {:description
      ["The filesystem location to make the directory at."]
      :type :string}]
    [:owner
     {:description
      ["The user that should have ownership over the directory. Can be specified as the username or the user id."]
      :type [:integer :string]}]
    [:group
     {:description
      ["The group that should have ownership over the directory. Can be specified as the groupname or the group id."]
      :type [:integer :string]}]
    [:mode
     {:description
      ["Set the access mode of this file or files."
       "Can be specified as an octal value of the form 0xxx, as a decimal value, or as a change string as is accepted by the system chmod command (eg. \"u+rwx\")."]
      :type [:integer :string]}]]
   :examples
   [
    {:description "Make a directory to store a website in on nginx"
     :form "
(mkdir {:path \"/var/www/mysite.mydomain\"
        :owner \"www-data\"
        :group \"www-data\"})"}]})
