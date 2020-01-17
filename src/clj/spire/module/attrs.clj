(ns spire.module.attrs
  (:require [spire.utils :as utils]
            [spire.nio :as nio]
            [spire.ssh :as ssh]
            [spire.facts :as facts]
            [spire.state :as state]
            [clojure.java.io :as io]
            [clojure.string :as string]))

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

(defn set-attrs [session opts]
  (let [bash-script (make-script opts)]
    (facts/on-shell
     :bash (ssh/ssh-exec session bash-script "" "UTF-8" {})
     :else (ssh/ssh-exec session "bash" bash-script "UTF-8" {}))))



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
                          access (utils/double-quote (nio/timestamp->touch access))
                          modified (utils/double-quote (nio/timestamp->touch modified))
                          (utils/path-quote filename)))
                       ["exit $EXIT"])
        script-string (string/join "\n" script)]
    (str header "\n" script-string)))

(defn set-attrs-preserve [session src dest]
  (let [script (make-preserve-script (create-attribute-list src) dest)]
    (prn 'set-attrs-preserve session src dest)
    (println script)
    (ssh/ssh-exec
     session
     script
     "" "UTF-8" {}))
  )
