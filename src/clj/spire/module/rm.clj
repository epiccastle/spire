(ns spire.module.rm
  (:require [spire.ssh :as ssh]
            [spire.utils :as utils]))

(utils/defmodule rm* [file-path]
  [host-string session {:keys [exec-fn shell-fn stdin-fn] :as shell-context}]
  (let [{:keys [exit out err] :as result}
        (exec-fn session
                 (shell-fn (format "rm %s" (utils/path-quote file-path)))
                 (stdin-fn "")
                 "UTF-8" {})]
    (assoc result
           :result (if (zero? exit) :ok :failed))))

(defmacro rm [& args]
  `(utils/wrap-report ~&form (rm* ~@args)))

(def documentation
  {
   :module "rm"
   :blurb "Delete a remote file or directory"
   :description
   [
    "This module returns the contents of a file on the remote machine"]
   :form "(rm file-path)"
   :args
   [{:arg "file-path"
     :desc "The path on the remote filesystem to the file"}]})
