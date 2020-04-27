(ns spire.module.get-file
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]
            [clojure.string :as string]))

(utils/defmodule get-file* [file-path]
  [host-string session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (let [{:keys [exit out err] :as result}
        (exec-fn session
                 (shell-fn (format "cat %s" (utils/path-quote file-path)))
                 (stdin-fn "")
                 "UTF-8" {})]
    (assoc result
           :out-lines (string/split-lines out)
           :err-lines (string/split-lines err)
           :result (if (zero? exit) :ok :failed))))

(defmacro get-file [& args]
  `(utils/wrap-report ~&form (get-file* ~@args)))

(def documentation
  {
   :module "get-file"
   :blurb "Gather the contents of a remote file"
   :description
   [
    "This module returns the contents of a file on the remote machine"]
   :form "(get-file file-path)"
   :args
   [{:arg "file-path"
     :desc "The path on the remote filesystem to the file"}]})
