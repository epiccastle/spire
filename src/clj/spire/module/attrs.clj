(ns spire.module.attrs
  (:require [spire.utils :as utils]
            [spire.nio :as nio]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.state :as state]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(defn preflight [{:keys [path owner group mode dir-mode attrs recurse]}]
  (facts/check-bins-present [:bash :find :stat :chattr :chown :chmod :lsattr])
  )

(defn make-script [{:keys [path owner group mode dir-mode attrs recurse]}]
  (facts/on-os
   :linux (utils/make-script
           "attrs.sh"
           {:FILE (some->> path utils/path-escape)
            :OWNER owner
            :GROUP group
            :MODE (if (number? mode) (format "%o" mode)  mode)
            :DIR_MODE (if (number? dir-mode) (format "%o" dir-mode)  dir-mode)
            :ATTRS attrs
            :RECURSE (if recurse "1" nil)})
   :else (utils/make-script
           "attrs_bsd.sh"
           {:FILE (some->> path utils/path-escape)
            :OWNER owner
            :GROUP group
            :MODE (if (number? mode) (format "%o" mode)  mode)
            :DIR_MODE (if (number? dir-mode) (format "%o" dir-mode)  dir-mode)
            :ATTRS attrs
            :RECURSE (if recurse "1" nil)})))

(defn process-result [{:keys [path owner group mode dir-mode attrs recurse] :as opts} {:keys [out err exit] :as result}]
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

(defn set-attrs [session opts]
  (let [bash-script (make-script opts)
        {:keys [exec-fn shell-fn stdin-fn]} (state/get-shell-context)]
    (exec-fn session (shell-fn "bash") (stdin-fn bash-script) "UTF-8" {})))



#_
(make-script "p" "o" "g" "m" "a")

(defn get-mode-and-times [origin file]
  [(nio/file-mode file)
   (nio/last-access-time file)
   (nio/last-modified-time file)
   (str "./" (nio/relativise origin file))])

(defn create-attribute-list [file]
  (let [file (io/file file)]
    (assert (.isDirectory file) "attribute tree must be passed a directory")
    (mapv #(get-mode-and-times file %) (file-seq file))))

#_ (create-attribute-list "test/files")

(defn make-preserve-script [perm-list dest]
  (let [header (facts/on-os
                :linux (utils/embed-src "attrs_preserve.sh")
                :else (utils/embed-src "attrs_preserve_bsd.sh"))
        script (concat [(format "cd %s" (utils/path-quote dest))
                        ]
                       (for [[mode access modified filename] perm-list]
                         (format
                          "set_file %o %d %s %d %s %s"
                          mode
                          access
                          (utils/double-quote
                           (facts/on-os
                            :linux (nio/timestamp->touch access)
                            :else (nio/timestamp->touch-bsd access)))
                          modified
                          (utils/double-quote
                           (facts/on-os
                            :linux (nio/timestamp->touch modified)
                            :else (nio/timestamp->touch-bsd modified)))
                          (utils/path-quote filename)))
                       ["exit $EXIT"])
        script-string (string/join "\n" script)]
    (str header "\n" script-string)))

(defn set-attrs-preserve [session src dest]
  (let [script (make-preserve-script (create-attribute-list src) dest)
        {:keys [exec-fn shell-fn stdin-fn]} (state/get-shell-context)]
    ;; (prn 'set-attrs-preserve src dest)
    ;; (println "---------------")
    ;; (println script)
    ;; (println "---------------")
    (let [result (exec-fn session (shell-fn "bash") (stdin-fn script) "UTF-8" {})]
      ;; (prn 'result (:exit result))
      ;; (println "---------------")
      ;; (println (:out result))
      ;; (println "---------------")

      result
      )))

(utils/defmodule attrs* [{:keys [path owner group mode dir-mode attrs recurse] :as opts}]
  [host-string session {:keys [exec-fn sudo] :as shell-context}]
  (or
   (preflight opts)
   (let [result
         (->>
          (exec-fn session "bash" (make-script opts) "UTF-8" {:sudo sudo})
          (process-result opts))]
     result))
  )

(defmacro attrs
  "Ensure a file or directory has the specified ownership and
  modification parameters.
  (atts opts)

  given:

  `opts`: a hashmap of options with the following keys:

  `:path` The file or directory location

  `:owner` Who should own the file or directory. Can be username or
  user id.

  `:group` The group that should own the file or directory. Can be a
  group name or a group id.

  `:mode` The access mode of ths of this file or files. Can be an
  octal literal (eg. `0xxx`), a string of the same form (eg `\"0xxx\")
  or a string of the change form (eg. `\"u+rwx\"`).

  `:dir-mode` The access mode for this directory or subdirectories
  when issued recursively.

  `:recurse` Recurse into the directory and change the attrs on all
  sub files and folders.
  "
  [& args]
  `(utils/wrap-report ~&form (attrs* ~@args)))

(def documentation
  {
   :module "attrs"
   :blurb "Ensure a file or directory has the specified ownership and modification parameters"
   :description
   [
    "This module ensures that a path has the specified ownership and modification flags."]
   :form "(attrs options)"
   :args
   [{:arg "options"
     :desc "A hashmap of options"}]
   :opts
   [[:path
     {:description
      ["The location of the file or directory to operate on"]
      :type :string}]
    [:owner
     {:description
      ["The user that should have ownership over the file or directory. Can be specified as the username or the user id."]
      :type [:integer :string]}]
    [:group
     {:description
      ["The group that should have ownership over the file or directory. Can be specified as the groupname or the group id."]
      :type [:integer :string]}]
    [:mode
     {:description
      ["Set the access mode of this file or files."
       "Can be specified as an octal value of the form 0xxx, as a decimal value, or as a change string as is accepted by the system chmod command (eg. \"u+rwx\")."]
      :type [:integer :string]}]
    [:dir-mode
     {:description
      ["Set the access mode of any directory or directories."
       "User when settings attributes recursively."
       "Can be specified as an octal value of the form 0xxx, as a decimal value, or as a change string as is accepted by the system chmod command (eg. \"u+rwx\")."]
      :type [:integer :string]}]
    [:recurse
     {:description
      ["Recurse into subdirectories and set the attributes of all files and directories therein."]
      :type [:boolean]}]

    ]
   :examples
   [
    {:description "Make a directory to store a website in on nginx"
     :form "
(attrs {:path \"/var/www/mysite.mydomain\"
        :owner \"www-data\"
        :group \"www-data\"})"}]})
