(ns spire.selmer
  (:require [clojure.java.io :as io]
            [selmer.parser :as parser]))

(defn selmer [source vars & options]
  (let [flags (into #{} options)
        cwd (or (some-> *file* io/file .getParent) ".")
        pre-markup (if (:data flags)
                     source
                     (slurp (io/input-stream (io/file cwd source))))]
    (parser/render pre-markup vars)))
