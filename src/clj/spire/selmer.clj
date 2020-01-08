(ns spire.selmer
  (:require [clojure.java.io :as io]
            [selmer.parser :as parser]))

(defn selmer [source vars & options]
  (let [flags (into #{} options)
        pre-markup (if (:data flags)
                     source
                     (slurp (io/input-stream source)))]
    (parser/render pre-markup vars)))
