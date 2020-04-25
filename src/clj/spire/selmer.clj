(ns spire.selmer
  (:require [spire.utils :as utils]
            [clojure.java.io :as io]
            [selmer.parser :as parser]))

(defn selmer [source vars & options]
  (let [flags (into #{} options)
        cwd (utils/current-file-parent)
        pre-markup (if (:data flags)
                     source
                     (slurp (io/input-stream (io/file cwd source))))]
    (parser/render pre-markup vars)))
