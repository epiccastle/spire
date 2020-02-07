(ns spire.module.get-file
  (:require [spire.output :as output]
            [spire.state :as state]
            [spire.transport :as transport]
            [spire.ssh :as ssh]
            [spire.utils :as utils]
            [clojure.string :as string]))

(utils/defmodule get-file* [file-path]
  [host-string session]
  (let [{:keys [exit out err] :as result}
        (ssh/ssh-exec session
                      (format "cat %s" (utils/path-quote file-path))
                      ""
                      "UTF-8" {})]
    (assoc result
           :result :ok)))

(defmacro get-file [& args]
  `(utils/wrap-report ~*file* ~&form (get-file* ~@args)))

(defmacro apt-repo [& args]
  `(utils/wrap-report ~*file* ~&form (apt-repo* ~@args)))

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
