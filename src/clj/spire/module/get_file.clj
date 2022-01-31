(ns spire.module.get-file
  (:require [spire.utils :as utils]
            [clojure.string :as string]))

(utils/defmodule get-file* [file-path]
  [host-string session {:keys [exec-fn sudo] :as shell-context}]
  (let [{:keys [exit out err] :as result}
        (exec-fn session
                 (format "cat %s" (utils/path-quote file-path))
                 ""
                 "UTF-8" {})]
    (assoc result
           :out-lines (string/split-lines out)
           :err-lines (string/split-lines err)
           :result (if (zero? exit) :ok :failed))))

(defmacro get-file
  "return the contents of a file on the remote machine
  (get-file file-path)

  given:

  `file-path`: the path of the file on the remote system.
  "
  [& args]
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
